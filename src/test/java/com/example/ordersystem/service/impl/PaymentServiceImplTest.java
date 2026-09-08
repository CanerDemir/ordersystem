package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.PaymentResultDto;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Payment;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.PaymentMethod;
import com.example.ordersystem.enums.PaymentStatus;
import com.example.ordersystem.exception.GatewayContractViolationException;
import com.example.ordersystem.exception.OrderCannotBePaidException;
import com.example.ordersystem.exception.PaymentFailedException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.gateway.PaymentGateway;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentAuditService paymentAuditService;

    @InjectMocks
    private PaymentServiceImpl paymentServiceImpl;

    private CurrentUser currentUser;
    private PaymentRequest paymentRequest;
    private Order pendingOrder;
    private Order paidOrder;
    private Customer customer;

    @BeforeEach
    void setUp() {
        currentUser = new CurrentUser(1L);
        paymentRequest = new PaymentRequest(PaymentMethod.CREDIT_CARD, "IDEMPOTENCY_KEY_123");
        customer = new Customer("Caner", "Demir", "c@c.com", "01234567890", "password");
        customer.setId(1L);

        // PENDING durumunda test siparişi (OrderId: 100L, CustomerId: 1L, TotalAmount: 250.00)
        pendingOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now());
        pendingOrder.setId(100L);

        paidOrder = new Order(OrderStatus.PAID, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now());
        paidOrder.setId(100L);
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 1 - Should process payment successfully, update order status to PAID and return PaymentResponse")
    void processOrderPayment_Success() {
        // GIVEN
        Long orderId = pendingOrder.getId();
        String idempotencyKey = paymentRequest.idempotencyKey();
        String transactionRef = "TX_REF_99999";

        // 1 & 3. Idempotency checks (Fast-path ve Double-check) henüz ödeme olmadığını belirtir
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Lock & IDOR Protection: Sipariş bulunur
        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId()))
                .thenReturn(Optional.of(pendingOrder));

        // 4. External Gateway başarılı yanıt döner
        when(paymentGateway.processPayment(any()))
                .thenReturn(new PaymentResultDto(true, transactionRef, null));

        // Payment kaydetme simülasyonu
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            return invocation.<Payment>getArgument(0); // Kaydedilen nesneyi geri dön
        });

        // WHEN
        PaymentResponse response = paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser);

        // THEN
        // 1. Response doğrulamaları
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.transactionReference()).isEqualTo(transactionRef);

        // 2. State Mutation Doğrulaması: Order status PAID olmalı
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // 3. Saved Payment Entity Doğrulamaları
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getOrderId()).isEqualTo(orderId);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(savedPayment.getTransactionReference()).isEqualTo(transactionRef);

        // 4. Failed Audit Service çağrılmamalı
        verifyNoInteractions(paymentAuditService);
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 2- Gateway failure should trigger AuditService, throw PaymentFailedException and leave Order PENDING")
    void processOrderPayment_Failed() {
        // GIVEN
        Long orderId = pendingOrder.getId();
        String idempotencyKey = paymentRequest.idempotencyKey();
        String errorMessage = "Insufficient funds";

        // 1 & 3. Idempotency checks boş döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Lock & IDOR Protection: Sipariş getirilir
        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId()))
                .thenReturn(Optional.of(pendingOrder));

        // 4. Gateway FAILED döner
        when(paymentGateway.processPayment(any()))
                .thenReturn(new PaymentResultDto(false, null, errorMessage));

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining(errorMessage);

        // 1. Audit Service'in REQUIRES_NEW metodu doğru parametrelerle tetiklendi mi?
        verify(paymentAuditService, times(1)).recordFailedPayment(
                eq(orderId),
                eq(customer.getId()),
                eq(new BigDecimal("250.00")),
                eq(PaymentMethod.CREDIT_CARD),
                eq(idempotencyKey)
        );

        // 2. State Mutation Doğrulaması: Order durumu PENDING olarak KALMALI (markAsPaid çağrılmadı)
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        // 3. Ana flow'daki SUCCESS Payment save metodu ÇAĞRILMAMALI
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 3 - Should return existing payment response without calling gateway when SUCCESS payment exists")
    void processOrderPayment_IdempotencySuccess_ShouldNotCallGateway() {
        // GIVEN
        Long orderId = 100L;
        String idempotencyKey = paymentRequest.idempotencyKey();
        String transactionRef = "TX_EXISTING_12345";

        // Daha önce veritabanına başarıyla kaydedilmiş Payment nesnesi
        Payment existingSuccessPayment = new Payment(
                orderId,
                currentUser.customerId(),
                new BigDecimal("250.00"),
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.SUCCESS,
                idempotencyKey,
                transactionRef
        );

        // Fast-path Idempotency check mevcut SUCCESS kaydı döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.of(existingSuccessPayment));

        // WHEN
        PaymentResponse response = paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser);

        // THEN
        // 1. Response doğrulamaları
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.transactionReference()).isEqualTo(transactionRef);

        // 2. Critical Assertion: Gateway ve OrderRepository asla çağrılmamalıdır
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(paymentAuditService);

        // 3. Veritabanına yeni bir kayıt atılmamalıdır
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 4 - Should throw PaymentFailedException without calling gateway when FAILED payment exists with same key")
    void processOrderPayment_IdempotencyFailed_ShouldNotCallGatewayAndThrowException() {
        // GIVEN
        Long orderId = 100L;
        String idempotencyKey = paymentRequest.idempotencyKey();

        // Daha önce veritabanına FAILED olarak kaydedilmiş Payment nesnesi
        Payment existingFailedPayment = new Payment(
                orderId,
                currentUser.customerId(),
                new BigDecimal("250.00"),
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.FAILED,
                idempotencyKey,
                null
        );

        // Fast-path Idempotency check mevcut FAILED kaydı döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.of(existingFailedPayment));

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Previous payment attempt with this idempotency key failed");

        // Critical Assertions: External gateway, DB kilitleri ve audit servisi tekrar çağrılmamalıdır
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(paymentAuditService);

        // Mükerrer kayıt atılmamalıdır
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 5 - Should throw ResourceNotFoundException and skip gateway call when order does not exist or belong to user")
    void processOrderPayment_OrderNotFound_ShouldThrowResourceNotFoundException() {
        // GIVEN
        Long invalidOrUnauthorizedOrderId = 999L;
        String idempotencyKey = paymentRequest.idempotencyKey();

        // 1. Fast-path idempotency check boş döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(invalidOrUnauthorizedOrderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Lock & IDOR Protection: Sipariş verilen customerId için bulunamaz
        when(orderRepository.findByIdAndCustomerIdWithLock(invalidOrUnauthorizedOrderId, currentUser.customerId()))
                .thenReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(invalidOrUnauthorizedOrderId, paymentRequest, currentUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with id: " + invalidOrUnauthorizedOrderId);

        // Critical Assertions: External Gateway, Audit Service ve DB Kayıt adımları kesinlikle tetiklenmemelidir
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(paymentAuditService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 6 - Should throw OrderCannotBePaidException and skip gateway call when order is already PAID")
    void processOrderPayment_OrderAlreadyPaid_ShouldThrowOrderCannotBePaidException() {
        // GIVEN
        Long orderId = paidOrder.getId();
        String idempotencyKey = paymentRequest.idempotencyKey();

        // 1. Fast-path idempotency check boş döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Lock & IDOR Protection: Zaten PAID olan sipariş getirilir
        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId()))
                .thenReturn(Optional.of(paidOrder));

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser))
                .isInstanceOf(OrderCannotBePaidException.class)
                .hasMessageContaining("Order is not in a payable state");

        // Critical Assertions: External Gateway, Audit Service ve DB Kayıt adımları kesinlikle tetiklenmemelidir
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(paymentAuditService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 7 - Gateway returns SUCCESS but null transactionReference should throw IllegalStateException and trigger audit")
    void processOrderPayment_GatewaySuccessWithNullTxRef_ShouldThrowExceptionAndTriggerAudit() {
        // GIVEN
        Long orderId = pendingOrder.getId();
        String idempotencyKey = paymentRequest.idempotencyKey();

        // 1. Idempotency checks boş döner
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(orderId, currentUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Sipariş getirilir
        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, currentUser.customerId()))
                .thenReturn(Optional.of(pendingOrder));

        // 3. Gateway successful=true döner FAKAT transactionReference NULL'dır (Bozuk/Hatalı Kontrat)
        PaymentResultDto invalidGatewayResponse = new PaymentResultDto(true, null, null);
        when(paymentGateway.processPayment(any()))
                .thenReturn(invalidGatewayResponse);

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(orderId, paymentRequest, currentUser))
                .isInstanceOf(GatewayContractViolationException.class)
                .hasMessageContaining("Payment provider returned successful=true but transactionReference was null or blank");

        // VERIFICATIONS:
        // 1. Mevcut recordFailedPayment metodu kontrat ihlali mesajıyla çağrılmalıdır
        verify(paymentAuditService, times(1)).recordFailedPayment(
                eq(orderId),
                eq(currentUser.customerId()),
                eq(new BigDecimal("250.00")),
                eq(PaymentMethod.CREDIT_CARD),
                eq(idempotencyKey)
        );

        // 2. Sipariş durumu PENDING kalmalıdır
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        // 3. Veritabanına hatalı/tutarsız payment kaydı atılmamalıdır
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOrderPayment Unit Test 8 - Existing SUCCESS payment owned by Customer 1 should throw ResourceNotFoundException for Customer 2 (IDOR Prevention)")
    void processOrderPayment_ExistingPaymentOwnedByOtherCustomer_ShouldNotBypassAuthorizationAndThrowException() {
        // GIVEN
        Long orderId = 100L;
        Long unauthorizedCustomerId = 2L;
        String idempotencyKey = "SHARED_IDEMPOTENCY_KEY_123";

        CurrentUser unauthorizedUser = new CurrentUser(unauthorizedCustomerId);
        PaymentRequest request = new PaymentRequest(PaymentMethod.CREDIT_CARD, idempotencyKey);

        // Customer 1'e ait sistemde mevcut SUCCESS bir ödeme
        Payment customerOnePayment = new Payment(
                orderId,
                currentUser.customerId(),
                new BigDecimal("250.00"),
                PaymentMethod.CREDIT_CARD,
                PaymentStatus.SUCCESS,
                idempotencyKey,
                "TX_CUSTOMER_ONE_999"
        );

        // 1. Fast-Path Check: Customer 2 (unauthorizedUser) sorgulandığında DB boş dönmelidir.
        // Başka müşterinin ödemesi kesinlikle bu sorguya takılmamalıdır.
        when(paymentRepository.findByOrderIdAndCustomerIdAndIdempotencyKey(
                orderId, unauthorizedUser.customerId(), idempotencyKey))
                .thenReturn(Optional.empty());

        // 2. Lock & Order Ownership Check: Sipariş, Customer 2 için veritabanında bulunamaz (IDOR Koruması)
        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, unauthorizedUser.customerId()))
                .thenReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> paymentServiceImpl.processOrderPayment(orderId, request, unauthorizedUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found with id: " + orderId);

        // CRITICAL SECURITY ASSERTIONS:
        // 1. Yetkisiz kullanıcıya mevcut ödeme bilgisi (PaymentResponse) KESİNLİKLE sızdırılmamalıdır.
        // 2. Gateway ve Audit servisleri asla tetiklenmemelidir.
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(paymentAuditService);

        // 3. Veritabanına yeni bir ödeme kaydı atılmamalıdır.
        verify(paymentRepository, never()).save(any());
    }
}
