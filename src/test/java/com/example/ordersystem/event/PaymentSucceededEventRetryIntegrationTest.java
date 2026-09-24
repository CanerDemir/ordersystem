package com.example.ordersystem.event;

import com.example.ordersystem.repository.ProcessedEventRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
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
class PaymentSucceededEventRetryIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @MockitoSpyBean
    private PaymentSucceededEventHandler paymentSucceededEventHandler;

    @Value("${kafka.consumer.topic}")
    private String mainTopic;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        processedEventRepository.deleteAll();

        String groupId = "test-retry-dlt-group-" + UUID.randomUUID();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker)
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        dltConsumer = consumerFactory.createConsumer();
        dltConsumer.subscribe(Collections.singletonList(mainTopic + ".DLT"));
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    @DisplayName("Retryable Exception -> Toplam 5 deneme (1 ilk + 4 retry) sonrası mesaj DLT topic'ine fırlatılmalı")
    void shouldRetryFourTimesAndSendToDltWhenDataAccessExceptionOccurs() throws ExecutionException, InterruptedException {
        // Arrange
        UUID eventId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String validEventPayload = createValidPaymentSucceededPayload(eventId, 3003L, 2002L, 4004L);

        // Handler sürekli kilitlenme / DB hatası fırlatacak şekilde stub'lanıyor
        doThrow(new CannotAcquireLockException("Database lock acquisition timeout"))
                .when(paymentSucceededEventHandler).handle(any());

        // Act
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // Assert 1: Handler tam olarak 5 kez çağrılmalı (Initial + 4 Retries)
        verify(paymentSucceededEventHandler, timeout(20000).times(5)).handle(any());

        // Assert 2: DLT topic'inden mesaj okunmalı
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);

        ConsumerRecord<String, String> dltRecord = records.iterator().next();
        assertThat(dltRecord.value()).isEqualTo(validEventPayload);

        // Assert 3: Null-Safe Header Assertion
        Header exceptionClassHeader = dltRecord.headers().lastHeader("kafka_dlt-exception-fqcn");
        assertThat(exceptionClassHeader)
                .as("kafka_dlt-exception-fqcn header must be present in DLT record")
                .isNotNull();

        String exceptionClassName = new String(exceptionClassHeader.value());
        assertThat(exceptionClassName).contains("CannotAcquireLockException");

        // Assert 4: İşlem başarısız olduğu için veritabanına kayıt atılmamalı
        assertThat(shipmentRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Transient Exception -> İlk denemede hata alıp 2. denemede (1. retry) başarılı olmalı, ilgili event DLT'ye DÜŞMEMELİ")
    void shouldSucceedOnSecondAttemptAndNotSendToDltWhenTransientErrorIsResolved() throws ExecutionException, InterruptedException {
        // Arrange
        UUID eventId = UUID.fromString("987e6543-e21b-12d3-a456-426614174000");
        String validEventPayload = createValidPaymentSucceededPayload(eventId, 3004L, 2003L, 4005L);

        // 1. çağrıda TransientDataAccessException fırlat, 2. çağrıda gerçek metoda geç
        doThrow(new TransientDataAccessException("Temporary network glitch") {})
                .doCallRealMethod()
                .when(paymentSucceededEventHandler).handle(any());

        // Act
        kafkaTemplate.send(mainTopic, validEventPayload).get();

        // Assert 1: Handler ilk deneme + 1 retry olmak üzere TOPLAM 2 kez çağrılmalı
        verify(paymentSucceededEventHandler, timeout(10000).times(2)).handle(any());

        // Assert 2: DLT topic'i taranır ve ilgili eventId'ye ait HİÇBİR mesajın DLT'ye düşmediği doğrulanır
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(3));

        boolean isEventPresentInDlt = StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .filter(Objects::nonNull)
                .anyMatch(payload -> payload.contains(eventId.toString()));

        assertThat(isEventPresentInDlt)
                .as("Event with eventId [%s] should NOT be present in DLT topic upon successful retry", eventId)
                .isFalse();

        // Assert 3: Retry başarılı olduğu için DB işlemleri eksiksiz tamamlanmış olmalı
        assertThat(shipmentRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.existsByEventId(eventId.toString())).isTrue();
    }

    // Helper method: PaymentSucceededEvent record yapısına %100 uyumlu JSON üretir
    private String createValidPaymentSucceededPayload(UUID eventId, Long paymentId, Long orderId, Long customerId) {
        return String.format("""
                {
                    "eventId": "%s",
                    "paymentId": %d,
                    "orderId": %d,
                    "customerId": %d,
                    "amount": 150.00,
                    "paidAt": "2026-09-24T10:00:00Z"
                }
                """, eventId, paymentId, orderId, customerId);
    }
}