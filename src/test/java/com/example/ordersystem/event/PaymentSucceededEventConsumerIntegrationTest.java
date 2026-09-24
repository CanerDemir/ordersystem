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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 3,
        topics = {"${kafka.consumer.topic}", "${kafka.consumer.topic}.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class PaymentSucceededEventConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${kafka.consumer.topic}")
    private String mainTopic;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        processedEventRepository.deleteAll();

        String groupId = "test-dlt-group-" + UUID.randomUUID();

        // DLT topic'ini dinleyecek test consumer'ı hazırlanıyor
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
    @DisplayName("Non-retryable Exception → mesaj DLT topic'ine yönlendirilmeli")
    void shouldSendToDltImmediatelyWhenNonRetryableDeserializationExceptionOccurs() throws ExecutionException, InterruptedException {
        // Arrange
        String malformedJsonPayload = "{ \"eventId\": \"invalid-json-structure\", orderId: ";

        // Act: Ana topic'e bozuk JSON gönderiliyor
        kafkaTemplate.send(mainTopic, malformedJsonPayload).get();

        // Assert 1: DLT topic'inden mesaj okunur (Maksimum 5 saniye bekleme)
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(5));

        assertThat(records.count()).isEqualTo(1);

        ConsumerRecord<String, String> dltRecord = records.iterator().next();

        // Assert 2: DLT'ye düşen mesajın payload'ı orijinal gönderilen payload ile BİREBİR aynı olmalı
        assertThat(dltRecord.value()).isEqualTo(malformedJsonPayload);

        // Assert 3: Vertabanına hiçbir hatalı kayıt atılmamalı
        assertThat(processedEventRepository.count()).isZero();
        assertThat(shipmentRepository.count()).isZero();

        // Assert 4: Null-Safe Spring Kafka DLT Header Kontrolü
        Header exceptionClassHeader =
                dltRecord.headers().lastHeader("kafka_dlt-exception-fqcn");

        // 4a. Header'ın null olmadığını garanti ediyoruz
        assertThat(exceptionClassHeader)
                .as("kafka_dlt-exception-fqcn header should be present in DLT record")
                .isNotNull();

        // 4b. Exception class name kontrolü
        String exceptionClassName = new String(exceptionClassHeader.value());
        assertThat(exceptionClassName).contains("EventDeserializationException");
    }
}