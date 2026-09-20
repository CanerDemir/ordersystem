package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.*;
import com.example.ordersystem.event.OutboxService;
import com.example.ordersystem.event.PaymentSucceededEvent;
import com.example.ordersystem.gateway.FakePaymentGateway;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.OutboxEventRepository;
import com.example.ordersystem.repository.PaymentRepository;
import com.example.ordersystem.service.interfaces.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class PaymentTransactionalOutboxIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private ObjectMapper objectMapper;

    @MockitoSpyBean
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        fakePaymentGateway.resetCallCount();
    }

    @Test
    @DisplayName("1. Happy Path DB Persistence: Order=PAID, Payment=SUCCESS, Outbox=PENDING are atomically committed to DB")
    void shouldPersistOrderPaymentAndOutboxAtomicallyOnSuccess() throws JsonProcessingException {
        // Given
        Customer customer = customerRepository.save(new Customer("Caner", "Demir", "c@c.com", "01234567890", "password"));
        Long customerId = customer.getId();
        CurrentUser currentUser = new CurrentUser(customerId);

        Order order = orderRepository.save(new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("150.00"), Instant.now()));
        Long orderId = order.getId();

        PaymentRequest requestDto = new PaymentRequest(
                PaymentMethod.CREDIT_CARD,
                "IDEMPOTENCY_KEY_HAPPY_PATH"
        );

        // When
        PaymentResponse resultPayment = paymentService.processOrderPayment(orderId, requestDto, currentUser);

        // Then - 1. Assert Payment Status in DB
        Payment persistedPayment = paymentRepository.findById(resultPayment.paymentId()).orElseThrow();
        assertThat(persistedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(persistedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

        // Then - 2. Assert Order Status in DB
        Order persistedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // Then - 3. Assert OutboxEvent Status & Content in DB
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outboxEvent = outboxEvents.getFirst();
        assertThat(outboxEvent.getAggregateType()).isEqualTo(AggregateType.PAYMENT);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(persistedPayment.getId());
        assertThat(outboxEvent.getEventType()).isEqualTo(EventType.PAYMENT_SUCCEEDED);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getPayload()).contains(orderId.toString());
        assertThat(outboxEvent.getPayload()).contains(persistedPayment.getId().toString());

        // Then - 4. Assert Outbox Payload against Integration Event Contract (Deserialization Check)
        PaymentSucceededEvent eventPayload = objectMapper.readValue(
                outboxEvent.getPayload(),
                PaymentSucceededEvent.class
        );

        assertThat(eventPayload).isNotNull();
        assertThat(eventPayload.eventId()).isEqualTo(outboxEvent.getEventId()); // Event Contract ID == Outbox Entity ID Alignment
        assertThat(eventPayload.paymentId()).isEqualTo(persistedPayment.getId());
        assertThat(eventPayload.orderId()).isEqualTo(orderId);
        assertThat(eventPayload.customerId()).isEqualTo(customerId);
        assertThat(eventPayload.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(eventPayload.paidAt()).isNotNull();
    }

    @Test
    @DisplayName("Transaction Rollback: Real Outbox save executes and fails right before commit, rolling back Payment and Order")
    void shouldRollbackAllDatabaseChangesWhenOutboxExecutionFailsAfterSave() {
        // Given 1: Persist Customer dynamically to avoid Sequence Allocation mismatch
        Customer customer = customerRepository.save(new Customer("Caner", "Demir", "c@c.com", "01234567890", "password"));
        Long customerId = customer.getId(); // Dynamic ID assignment (Deterministic)

        CurrentUser currentUser = new CurrentUser(customerId);

        // Given 2: Create Order in DB
        Order order = orderRepository.save(new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now()));
        Long orderId = order.getId();

        PaymentRequest requestDto = new PaymentRequest(
                PaymentMethod.CREDIT_CARD,
                "IDEMPOTENCY_KEY_REAL_ROLLBACK_TEST"
        );

        // Given 3: Mock OutboxService using doAnswer to force REAL method invocation BEFORE throwing exception
        doAnswer(invocation -> {
            // 1. Call REAL OutboxService method (Serializes, Creates OutboxEvent & calls Repository.save)
            invocation.callRealMethod();

            // 2. Outbox persistence işleminden sonra transaction rollback'e zorlanırsa üç değişiklik de rollback olur.
            throw new RuntimeException("Simulated exception right after OutboxEvent DB persistence");
        }).when(outboxService).recordPaymentSucceeded(any());

        // When & Then: Payment processing must fail due to the thrown exception
        assertThatThrownBy(() -> paymentService.processOrderPayment(orderId, requestDto, currentUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated exception right after OutboxEvent DB persistence");

        // Assert Rollback State: Transaction Rollback guarantees ZERO side effects in DB!
        Order rolledBackOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(rolledBackOrder.getStatus()).isEqualTo(OrderStatus.PENDING); // MUST REMAIN PENDING (NOT PAID)

        assertThat(paymentRepository.findAll()).isEmpty();      // Payment MUST NOT exist
        assertThat(outboxEventRepository.findAll()).isEmpty();  // OutboxEvent MUST NOT exist due to ROLLBACK
    }
}