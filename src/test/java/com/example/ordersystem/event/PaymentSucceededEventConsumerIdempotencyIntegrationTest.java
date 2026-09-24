package com.example.ordersystem.event;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ShipmentStatus;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProcessedEventRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 3,
        topics = {"${kafka.consumer.topic}"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class PaymentSucceededEventConsumerIdempotencyIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    CustomerRepository customerRepository;

    @MockitoSpyBean
    private PaymentSucceededEventHandler paymentSucceededEventHandler;

    @Value("${kafka.consumer.topic}")
    private String mainTopic;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Duplicate PaymentSucceededEvent -> Idempotency sayesinde ikinci Shipment oluşturulmamalı")
    void shouldIgnoreDuplicateEventAndPreventDuplicateShipment() throws ExecutionException, InterruptedException {
        // Arrange
        UUID eventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Long paymentId = 7001L;
        Customer customer = customerRepository.save(new Customer("Caner", "Demir", "c@c.com", "01234567890", "password"));
        Long customerId = customer.getId();

        Order order = orderRepository.save(new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("350.00"), Instant.now()));
        Long orderId = order.getId();

        String validEventPayload = createValidPaymentSucceededPayload(eventId, paymentId, orderId, customerId);

        // ACT 1: Mesaj Kafka'ya gönderilir (İlk Delivery)
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // ASSERT 1: İlk Invocation ve DB Commit Basarisi
        // Awaitility ile ilk işlemin DB'ye tam olarak yansıdığını doğruluyoruz
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    assertThat(processedEventRepository.count()).isEqualTo(1);
                    assertThat(shipmentRepository.count()).isEqualTo(1);
                });

        verify(paymentSucceededEventHandler, atLeast(1)).handle(any());

        // FAILURE WINDOW SIMULATION (DB Commit ✅, Offset Commit ❌ -> Redelivery 🔄)
        // Kafka offset commit başarsızlığı durumunda (network glitch, broker crash vb.),
        // partition offset'i güncellenmez ve consumer aynı offset'teki mesajı TEKRAR alır (Redelivery).
        // Bu durumu simüle etmek için container'ı durdurup, aynı mesajı Kafka'ya tekrar publish ediyoruz (veya seek ediyoruz).
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // ASSERT 2 & 3: Redelivery + Idempotency Assertions
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    // 1. Handler Redelivery Kanıtı: En az 2 kez çağrıldığını doğruluyoruz
                    verify(paymentSucceededEventHandler, atLeast(2)).handle(any());

                    // 2. ProcessedEvent Tablo Garantisi: 1. denemede yazıldı, 2. denemede duplicate tespit edildi -> Tam olarak 1 kayıt
                    assertThat(processedEventRepository.count())
                            .as("ProcessedEvent count must remain exactly 1 despite multiple redeliveries")
                            .isEqualTo(1);

                    assertThat(processedEventRepository.existsByEventId(eventId.toString()))
                            .as("ProcessedEvent record must exist in DB with the exact eventId")
                            .isTrue();

                    // 3. Side-Effect (Shipment) Garantisi: 2. denemede handler early return yaptığı için İKİNCİ SHIPMENT OLUŞMAMALI!
                    assertThat(shipmentRepository.count())
                            .as("Shipment count must be exactly 1. Duplicate shipment MUST NOT be created!")
                            .isEqualTo(1);

                    // 4. Domain Status Integrity
                    shipmentRepository.findAll().stream().findFirst().ifPresent(shipment -> {
                        assertThat(shipment.getOrder().getId()).isEqualTo(orderId);
                        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
                    });
                });

        // ASSERT 4: İkinci handler çağrısının da AYNI eventId ile gerçekleştiğinin kesin kanıtı
        ArgumentCaptor<com.example.ordersystem.event.PaymentSucceededEvent> eventCaptor =
                ArgumentCaptor.forClass(com.example.ordersystem.event.PaymentSucceededEvent.class);

        verify(paymentSucceededEventHandler, atLeast(2)).handle(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(event ->
                        assertThat(event.eventId()).isEqualTo(eventId));
    }

    // Helper: Valid Payload Builder
    private String createValidPaymentSucceededPayload(UUID eventId, Long paymentId, Long orderId, Long customerId) {
        return String.format("""
                {
                    "eventId": "%s",
                    "paymentId": %d,
                    "orderId": %d,
                    "customerId": %d,
                    "amount": 350.00,
                    "paidAt": "2026-09-24T10:00:00Z"
                }
                """, eventId, paymentId, orderId, customerId);
    }
}