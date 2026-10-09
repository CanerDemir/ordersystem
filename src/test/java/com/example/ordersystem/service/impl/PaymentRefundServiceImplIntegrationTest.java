package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.RefundRequest;
import com.example.ordersystem.dto.response.RefundResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.entity.PaymentRefund;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.enums.RefundStatus;
import com.example.ordersystem.exception.PaymentRefundException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.gateway.FakePaymentGateway;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.PaymentRefundRepository;
import com.example.ordersystem.repository.PaymentRepository;
import com.example.ordersystem.service.interfaces.PaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRefundServiceImplIntegrationTest {

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentRefundRepository paymentRefundRepository;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

    private Customer testCustomer;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        paymentRefundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        fakePaymentGateway.resetCallCount();

        // 1. Customer
        testCustomer = customerRepository.save(new Customer("John", "Doe", "john@example.com", "5551234567", "password1"));

        // 2. Order (PAID)
        testOrder = new Order(
                OrderStatus.PENDING,
                testCustomer,
                "5551234567",
                "John",
                "Doe",
                "john@example.com",
                BigDecimal.valueOf(150.00)
        );
        testOrder.markAsPaid();
        testOrder = orderRepository.save(testOrder);

        // 3. Successful Payment
        Payment testPayment = new Payment(
                testOrder.getId(),
                testCustomer.getId(),
                BigDecimal.valueOf(150.00),
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.SUCCESS,
                "PAYMENT-TEST-KEY",
                "TX-FAKE-abc");
        paymentRepository.save(testPayment);
    }

    private void shutdownExecutor(ExecutorService executor) {
        // Düzeltme 3: Güvenli ve Temiz Executor Kapatma Mantığı
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Test 1: Concurrent Same Key -> Sadece 1 gateway çağrısı, aynı refundId, DB'de 1 SUCCESS kayıt ve Order REFUNDED")
    void processRefund_concurrentSameKey_shouldExecuteGatewayOnceAndReturnSameRefundId() throws Exception {
        String idempotencyKey = "REFUND-SAME-KEY";
        RefundRequest request = new RefundRequest(idempotencyKey);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<RefundResponse> futureA = executor.submit(() -> {
                startLatch.await();
                return paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), request);
            });

            Future<RefundResponse> futureB = executor.submit(() -> {
                startLatch.await();
                return paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), request);
            });

            startLatch.countDown();

            // Future.get() exception'ı ExecutionException olarak doğrudan dışarı fırlatır
            RefundResponse responseA = futureA.get(10, TimeUnit.SECONDS);
            RefundResponse responseB = futureB.get(10, TimeUnit.SECONDS);

            assertNotNull(responseA);
            assertNotNull(responseB);

            assertEquals(RefundStatus.SUCCESS, responseA.status());
            assertEquals(RefundStatus.SUCCESS, responseB.status());
            assertEquals(responseA.refundId(), responseB.refundId());

            assertEquals(1, fakePaymentGateway.getRefundCallCount());

            List<PaymentRefund> refunds = paymentRefundRepository.findAll();
            assertEquals(1, refunds.size());
            assertEquals(RefundStatus.SUCCESS, refunds.getFirst().getStatus());

            Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
            assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());
            assertNotNull(updatedOrder.getRefundedAt());

        } finally {
            shutdownExecutor(executor);
        }
    }

    @Test
    @DisplayName("Test 2: Concurrent Different Key -> Tam olarak 1 RefundResponse ve 1 PaymentRefundException (ORDER_ALREADY_REFUNDED)")
    void processRefund_concurrentDifferentKey_shouldAllowOneSuccessAndThrowOrderAlreadyRefundedForOther() throws Exception {
        RefundRequest requestA = new RefundRequest("REFUND-KEY-A");
        RefundRequest requestB = new RefundRequest("REFUND-KEY-B");

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Object> futureA = executor.submit(() -> {
                startLatch.await();
                try {
                    return paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), requestA);
                } catch (Exception e) {
                    return e;
                }
            });

            Future<Object> futureB = executor.submit(() -> {
                startLatch.await();
                try {
                    return paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), requestB);
                } catch (Exception e) {
                    return e;
                }
            });

            startLatch.countDown();

            Object resA = futureA.get(10, TimeUnit.SECONDS);
            Object resB = futureB.get(10, TimeUnit.SECONDS);

            RefundResponse successResponse = null;
            PaymentRefundException thrownException = null;

            // Düzeltme 1: Beklenmeyen türler için açık ve net assertion mesajı ile başarısız olma
            if (resA instanceof RefundResponse r) {
                successResponse = r;
            } else if (resA instanceof PaymentRefundException e) {
                thrownException = e;
            } else {
                fail("Unexpected result type from Thread A: " + resA.getClass().getName());
            }

            if (resB instanceof RefundResponse r) {
                if (successResponse != null) {
                    fail("Both threads returned RefundResponse; expected one failure");
                }
                successResponse = r;
            } else if (resB instanceof PaymentRefundException e) {
                if (thrownException != null) {
                    fail("Both threads threw PaymentRefundException; expected one success");
                }
                thrownException = e;
            } else {
                fail("Unexpected result type from Thread B: " + resB.getClass().getName());
            }

            assertNotNull(successResponse, "İki sonuçtan biri kesinlikle RefundResponse olmalı");
            assertNotNull(thrownException, "İki sonuçtan biri kesinlikle PaymentRefundException olmalı");

            assertEquals(RefundStatus.SUCCESS, successResponse.status());
            assertEquals("ORDER_ALREADY_REFUNDED", thrownException.getErrorCode());

            assertEquals(1, fakePaymentGateway.getRefundCallCount());

            List<PaymentRefund> refunds = paymentRefundRepository.findAll();
            assertEquals(1, refunds.size(), "Çifte kayda izin verilmemeli");
            assertEquals(RefundStatus.SUCCESS, refunds.getFirst().getStatus());

            Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
            assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());
            assertNotNull(updatedOrder.getRefundedAt());

        } finally {
            shutdownExecutor(executor);
        }
    }

    @Test
    @DisplayName("Test 3: FAILED -> Yeni Key ile SUCCESS -> DB'de 1 FAILED ve 1 SUCCESS kayıt ayrı ayrı doğrulanmalı")
    void processRefund_whenPreviousRefundFailed_shouldAllowNewRefundWithDifferentKey() {
        String failedKey = "REFUND_FAIL_FIRST_TRY";
        RefundRequest failedRequest = new RefundRequest(failedKey);

        // 1. Başarısız ilk deneme
        RefundResponse failedResponse = paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), failedRequest);
        assertEquals(RefundStatus.FAILED, failedResponse.status());
        assertEquals(1, fakePaymentGateway.getRefundCallCount());

        // 2. Yeni key ile ikinci deneme
        String newKey = "REFUND_SUCCESS_SECOND_TRY";
        RefundRequest newRequest = new RefundRequest(newKey);
        RefundResponse successResponse = paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), newRequest);

        // Assert
        assertEquals(RefundStatus.SUCCESS, successResponse.status());
        assertEquals(2, fakePaymentGateway.getRefundCallCount());

        List<PaymentRefund> refunds = paymentRefundRepository.findAll();
        assertEquals(2, refunds.size());

        boolean hasFailedRecord = refunds.stream().anyMatch(r -> r.getStatus() == RefundStatus.FAILED && r.getIdempotencyKey().equals(failedKey));
        boolean hasSuccessRecord = refunds.stream().anyMatch(r -> r.getStatus() == RefundStatus.SUCCESS && r.getIdempotencyKey().equals(newKey));

        assertTrue(hasFailedRecord, "DB'de FAILED iade kaydı bulunmalı");
        assertTrue(hasSuccessRecord, "DB'de SUCCESS iade kaydı bulunmalı");

        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.REFUNDED, updatedOrder.getStatus());
        assertNotNull(updatedOrder.getRefundedAt());
    }

    @Test
    @DisplayName("Güvenlik Testi 1: Başka bir müşterinin sipariş kimliği ile istek atıldığında veri sızdırılmamalı (404 Not Found)")
    void processRefund_whenUsingIdempotencyKeyOfAnotherCustomer_shouldThrowResourceNotFoundException() {
        String sharedKey = "SHARED-KEY-123";
        RefundResponse customerARefund = paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), new RefundRequest(sharedKey));
        assertEquals(RefundStatus.SUCCESS, customerARefund.status());

        Customer customerB = customerRepository.save(new Customer("Alice", "Smith", "alice@example.com", "5559876543", "password2"));

        final Long orderAId = testOrder.getId();
        final Long customerBId = customerB.getId();

        // Müşteri B, Müşteri A'nın sipariş ID'sini gönderdiğinde veriyi alamayıp 404 fırlatmalı
        assertThrows(ResourceNotFoundException.class, () ->
                paymentRefundService.processRefund(orderAId, customerBId, new RefundRequest(sharedKey))
        );
    }

    @Test
    @DisplayName("Güvenlik Testi 2: Aynı idempotency key başka bir sipariş için kullanıldığında IDEMPOTENCY_KEY_CONFLICT fırlatmalı")
    void processRefund_whenSameIdempotencyKeyUsedForDifferentOrder_shouldThrowConflictException() {
        String reusedKey = "REUSED-KEY-999";
        paymentRefundService.processRefund(testOrder.getId(), testCustomer.getId(), new RefundRequest(reusedKey));

        // Müşteri 1'e ait 2. Sipariş
        Order order2 = new Order(OrderStatus.PENDING, testCustomer, "5551234567", "John", "Doe", "john@example.com", BigDecimal.valueOf(300.00));
        order2.markAsPaid();
        order2 = orderRepository.save(order2);

        Payment payment2 = new Payment(
                order2.getId(),
                testCustomer.getId(),
                BigDecimal.valueOf(300.00),
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.SUCCESS,
                "TX-TEST_002",
                "TX_FAKE_BCA");
        paymentRepository.save(payment2);

        final Long order2Id = order2.getId();
        final Long customerId = testCustomer.getId();

        PaymentRefundException exception = assertThrows(PaymentRefundException.class, () ->
                paymentRefundService.processRefund(order2Id, customerId, new RefundRequest(reusedKey))
        );

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", exception.getErrorCode());
    }
}