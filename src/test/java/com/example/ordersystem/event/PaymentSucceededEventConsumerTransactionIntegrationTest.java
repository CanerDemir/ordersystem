package com.example.ordersystem.event;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ShipmentStatus;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProcessedEventRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 3,
        topics = {"${kafka.consumer.topic}", "${kafka.consumer.topic}.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class PaymentSucceededEventConsumerTransactionIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @MockitoSpyBean
    private ShipmentRepository shipmentRepositorySpy;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @MockitoSpyBean
    private PaymentSucceededEventHandler paymentSucceededEventHandler;

    @Value("${kafka.consumer.topic}")
    private String mainTopic;

    private Consumer<String, String> dltConsumer;
    private Long customerId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();

        String groupId = "test-tx-dlt-group" + UUID.randomUUID();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker)
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        dltConsumer = consumerFactory.createConsumer();
        dltConsumer.subscribe(Collections.singletonList(mainTopic + ".DLT"));

        Customer customer = new Customer("Caner", "Demir", "c@c.com", "01234567890", "password");
        customer = customerRepository.save(customer);
        customerId = customer.getId();
        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now());
        order = orderRepository.save(order);
        orderId = order.getId();
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    @DisplayName("Test 1 — Success: Happy path 'te DB transaction commit edilmeli, Shipment ve ProcessedEvent eksiksiz oluşmalı")
    void shouldCommitTransactionAndPersistShipmentAndProcessedEventWhenSuccessful() throws ExecutionException, InterruptedException {
        // Arrange
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Long paymentId = 8001L;

        String validEventPayload = createValidPaymentSucceededPayload(eventId, paymentId, orderId, customerId);

        // Act: Kafka'ya geçerli mesaj gönderilir
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // Assert 1: Consumer mesajı işler
        verify(paymentSucceededEventHandler, timeout(10000).times(1)).handle(any());

        // Assert 2: DB Transaction Commit Kontrolü
        assertThat(processedEventRepository.count())
                .as("ProcessedEvent count must be exactly 1 upon successful commit")
                .isEqualTo(1);

        assertThat(processedEventRepository.existsByEventId(eventId.toString()))
                .as("ProcessedEvent with eventId must exist in database")
                .isTrue();

        assertThat(shipmentRepository.count())
                .as("Shipment count must be exactly 1 upon successful commit")
                .isEqualTo(1);

        // Assert 3: Domain State Kontrolü (Shipment status READY olmalı)
        shipmentRepository.findAll().stream().findFirst().ifPresentOrElse(
                shipment -> {
                    assertThat(shipment.getOrder().getId()).isEqualTo(orderId);
                    assertThat(shipment.getStatus())
                            .as("Shipment status must be READY after processing PaymentSucceededEvent")
                            .isEqualTo(ShipmentStatus.READY);
                },
                () -> assertThat(false).as("Shipment record should be present in database").isTrue()
        );
    }

    @Test
    @DisplayName("Test 2 — Transaction Rollback → ProcessedEvent and Shipment must be rolled back")
    void shouldRollbackBothShipmentAndProcessedEventWhenExceptionOccursInsideTransaction() throws ExecutionException, InterruptedException {
        // Arrange
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Long paymentId = 8002L;

        String validEventPayload = createValidPaymentSucceededPayload(eventId, paymentId, orderId, customerId);

        // GERÇEK AKIŞ ADIMLARI:
        // 1. ProcessedEvent INSERT (başarılı)
        // 2. Order SELECT (Order DB'de mevcut, bulunur)
        // 3. Shipment hazırlanır
        // 4. shipmentRepositorySpy.save() çağrılır -> RuntimeException fırlatılır -> ROLLBACK!
        doThrow(new RuntimeException("Simulated Database write failure during shipment save"))
                .when(shipmentRepositorySpy).save(any());

        // Act
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // Assert 1: DefaultErrorHandler retry mekanizması nedeniyle handler en az 1 kez tetiklenir
        verify(paymentSucceededEventHandler, timeout(10000).atLeast(1)).handle(any());

        // Assert 2: METODUN GERÇEKTEN ÇAĞRILDIĞININ KANITI
        // Akışın eksiksiz ilerlediğini ve hatanın TAM Olarak save() esnasında fırlatıldığını teyit ediyoruz
        verify(shipmentRepositorySpy, timeout(10000).atLeast(1)).save(any());

        // Assert 3: EVENTUAL ASSERTION VIA AWAITILITY
        // Retry ve Transaction Rollback süreçleri asenkron olarak tamamlanırken,
        // DB durumunun temiz kaldığını (rollback gerçekleştiğini) Awaitility ile polleyerek doğruluyoruz.
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    assertThat(processedEventRepository.count())
                            .as("ProcessedEvent must be ROLLED BACK to 0 so future retries are not falsely blocked by idempotency check")
                            .isZero();

                    assertThat(processedEventRepository.existsByEventId(eventId.toString()))
                            .as("ProcessedEvent ID must NOT exist in database due to transaction rollback")
                            .isFalse();

                    assertThat(shipmentRepository.count())
                            .as("Shipment count must be 0 due to transaction rollback")
                            .isZero();
                });
    }

    // Helper: Valid Payload Builder
    private String createValidPaymentSucceededPayload(UUID eventId, Long paymentId, Long orderId, Long customerId) {
        return String.format("""
                {
                    "eventId": "%s",
                    "paymentId": %d,
                    "orderId": %d,
                    "customerId": %d,
                    "amount": 250.00,
                    "paidAt": "2026-09-24T10:00:00Z"
                }
                """, eventId, paymentId, orderId, customerId);
    }
}