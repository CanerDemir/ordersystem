package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.exception.PaymentFailedException;
import com.example.ordersystem.gateway.FakePaymentGateway;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentServiceImplIntegrationTest {
    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long orderId;
    private CurrentUser currentUser;
    private PaymentRequest paymentRequest;
    private Customer customer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM payment_audit_logs");
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        fakePaymentGateway.resetCallCount();
        customer = new Customer("Caner", "Demir", "c@c.com", "01234567890", "password");

        // Testing DB Context: PENDING durumunda bir sipariş oluşturulur
        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now());
        order = orderRepository.save(order);

        this.orderId = order.getId();
        this.currentUser = new CurrentUser(customer.getId());
        this.paymentRequest = new PaymentRequest(PaymentMethod.CREDIT_CARD, "CONCURRENT_IDEMPOTENCY_KEY_999");
    }

    @Test
    @DisplayName("Concurrent Payment Request - 2 Threads with same idempotencyKey should result in 1 Gateway Call and 1 SUCCESS Payment")
    void processOrderPayment_ConcurrentExecution_ShouldEnsureIdempotencyAndSingleGatewayCall() throws InterruptedException, ExecutionException {
        // GIVEN
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // Threads'in tam olarak aynı anda başlamasını tetikleyen Latch
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        try {
            Callable<PaymentResponse> paymentTask = () -> {
                startLatch.await(); // Thread'ler hazırda bekler
                try {
                    return paymentService.processOrderPayment(orderId, paymentRequest, currentUser);
                } finally {
                    endLatch.countDown();
                }
            };

            // Thread A ve Thread B görevi alırlar
            Future<PaymentResponse> futureA = executorService.submit(paymentTask);
            Future<PaymentResponse> futureB = executorService.submit(paymentTask);

            // WHEN: İki thread tam aynı anda serbest bırakılır
            startLatch.countDown();
            boolean completedInTime = endLatch.await(5, TimeUnit.SECONDS);

            // THEN
            assertThat(completedInTime).isTrue();

            PaymentResponse responseA = futureA.get();
            PaymentResponse responseB = futureB.get();

            // 1. Thread A ve Thread B sonuçları eşleşmeli (Double-Check sayesinde B de başarılı döner)
            assertThat(responseA).isNotNull();
            assertThat(responseB).isNotNull();
            assertThat(responseA.transactionReference()).isEqualTo(responseB.transactionReference());
            assertThat(responseA.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(responseB.status()).isEqualTo(PaymentStatus.SUCCESS);

            // 2. CRITICAL PROOF: Gateway yalnızca 1 kez çağrıldı
            assertThat(fakePaymentGateway.getCallCount()).isEqualTo(1);

            // 3. CRITICAL PROOF: Veritabanında tam olarak 1 adet SUCCESS Payment kaydı olmalıdır
            Optional<Payment> savedPaymentOpt = paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(
                    orderId,
                    currentUser.customerId(),
                    paymentRequest.idempotencyKey()
            );

            assertThat(savedPaymentOpt).isPresent();
            Payment savedPayment = savedPaymentOpt.get();
            assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(savedPayment.getIdempotencyKey()).isEqualTo("CONCURRENT_IDEMPOTENCY_KEY_999");

            // 4. Mükerrer başka bir kayıt atılmadığını teyit ediyoruz (DB'deki toplam payment sayısı = 1)
            assertThat(paymentRepository.count()).isEqualTo(1);

            // 5. Sipariş durumu PAID olarak güncellendi
            Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Failed Payment - Outer Transaction Rollback Should Not Affect REQUIRES_NEW Audit Log Persistence")
    void processOrderPayment_FailedPayment_ShouldRollbackOuterTxButPersistAuditLog() {
        // GIVEN: FakePaymentGateway kuralı gereği FAIL_ öneki ile idempoten key hazırlanır
        String failIdempotencyKey = FakePaymentGateway.FAIL_KEY_PREFIX + "AUDIT_TEST_123";
        PaymentRequest paymentRequest = new PaymentRequest(PaymentMethod.CREDIT_CARD, failIdempotencyKey);

        // WHEN & THEN: Gateway ödemeyi reddeder ve servis PaymentFailedException fırlatır
        assertThatThrownBy(() ->
                paymentService.processOrderPayment(orderId, paymentRequest, currentUser)
        ).isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Payment rejected by fake gateway test rule.");

        // CRITICAL TRANSACTIONAL PROOFS:

        // 1. PROOF OF AUDIT PERSISTENCE (REQUIRES_NEW)
        // JdbcTemplate kullanılarak audit kaydının rollback'ten etkilenmeyip kalıcı olduğu doğrulanır
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_audit_logs WHERE order_id = ? AND idempotency_key = ?",
                Integer.class,
                orderId,
                failIdempotencyKey
        );
        assertThat(auditCount).isEqualTo(1);

        String auditDetails = jdbcTemplate.queryForObject(
                "SELECT details FROM payment_audit_logs WHERE order_id = ? AND idempotency_key = ?",
                String.class,
                orderId,
                failIdempotencyKey
        );
        assertThat(auditDetails).contains("Payment rejected by fake gateway test rule.");

        // 2. PROOF OF OUTER TRANSACTION ROLLBACK
        // Ana transaction rollback olduğu için veritabanında SUCCESS ödeme kaydı OLMAMALIDIR
        assertThat(paymentRepository.count()).isEqualTo(0);

        // 3. PROOF OF ORDER STATE PROTECTION
        // Sipariş durumu PAID yapılmamış, PENDING olarak ROLLBACK edilmiş olmalıdır
        Order persistedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
