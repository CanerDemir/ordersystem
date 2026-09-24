package com.example.ordersystem.event;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.ProcessedEvent;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ShipmentStatus;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProcessedEventRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentSucceededEventHandlerIntegrationTest {

    @Autowired
    private PaymentSucceededEventHandler eventHandler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();

        customer = customerRepository.save(new Customer("Caner", "Demir", "c@c.com", "01234567890", "password"));
    }

    private Order createAndSaveOrder() {
        Order order = new Order(OrderStatus.PAID, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("250.00"), Instant.now());
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("1. Yeni Event -> ProcessedEvent kaydı oluşur, Shipment READY durumunda yaratılır")
    void shouldCreateShipmentAndRecordProcessedEventWhenNewEventArrives() {
        // Arrange
        Order order = createAndSaveOrder();
        UUID eventId = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                eventId, 1L, order.getId(), order.getCustomer().getId(), order.getTotalAmount(), Instant.now()
        );

        // Act
        eventHandler.handle(event);

        // Assert
        Optional<ProcessedEvent> processedEvent = processedEventRepository.findByEventId(eventId.toString());
        assertThat(processedEvent).isPresent();

        Optional<Shipment> shipment = shipmentRepository.findByOrderId(order.getId());
        assertThat(shipment).isPresent();
        assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.get().getOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("2. Duplicate Event -> insertIfNotExists 0 döner, yeni Shipment oluşmaz, mevcut yapı korunur")
    void shouldIgnoreDuplicateEventAndNotCreateSecondShipment() {
        // Arrange
        Order order = createAndSaveOrder();
        UUID eventId = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                eventId, 1L, order.getId(), order.getCustomer().getId(), order.getTotalAmount(), Instant.now()
        );

        // İlk çağrı
        eventHandler.handle(event);
        assertThat(shipmentRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);

        // Act: Aynı event tekrar geliyor
        eventHandler.handle(event);

        // Assert
        assertThat(shipmentRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("3. Order Bulunamadı -> Exception fırlatılır, ProcessedEvent rollback edilir")
    void shouldRollbackProcessedEventWhenOrderNotFound() {
        // Arrange
        Long nonExistentOrderId = 999999L;
        UUID eventId = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                eventId, 1L, nonExistentOrderId, 100L, new BigDecimal("100.00"), Instant.now()
        );

        // Act & Assert
        assertThatThrownBy(() -> eventHandler.handle(event))
                .isInstanceOf(ResourceNotFoundException.class);

        // Transaction Rollback doğrulaması: processed_events tablosu boş kalmalı
        assertThat(processedEventRepository.findByEventId(eventId.toString())).isEmpty();
        assertThat(shipmentRepository.count()).isZero();
    }

    @Test
    @DisplayName("4. Shipment Creation/Persistence Failure -> Invariant ihlalinde (zaten shipment var) Hata fırlatılır ve ProcessedEvent rollback edilir")
    void shouldRollbackProcessedEventWhenShipmentCreationFails() {
        // Arrange
        Order order = createAndSaveOrder();

        // Veritabanında elle önceden bir Shipment oluşturup invariant'ı ihlal ediyoruz
        Shipment existingShipment = Shipment.createReady(order);
        shipmentRepository.save(existingShipment);

        UUID eventId = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                eventId, 1L, order.getId(), order.getCustomer().getId(), order.getTotalAmount(), Instant.now()
        );

        // Act & Assert
        assertThatThrownBy(() -> eventHandler.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Shipment already exists for orderId: " + order.getId());

        // Transaction Rollback doğrulaması: ikinci event için ProcessedEvent kaydedilmemeli
        assertThat(processedEventRepository.findByEventId(eventId.toString())).isEmpty();
        assertThat(shipmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. Aynı eventId + Farklı orderId -> Idempotency key eventId olduğu için ikinci event duplicate kabul edilip işlenmez")
    void shouldTreatSameEventIdWithDifferentOrderIdAsDuplicate() {
        // Arrange
        Order order1 = createAndSaveOrder();
        Order order2 = createAndSaveOrder();

        UUID sameEventId = UUID.randomUUID();

        PaymentSucceededEvent event1 = new PaymentSucceededEvent(
                sameEventId, 1L, order1.getId(), order1.getCustomer().getId(), order1.getTotalAmount(), Instant.now()
        );
        PaymentSucceededEvent event2 = new PaymentSucceededEvent(
                sameEventId, 2L, order2.getId(), order2.getCustomer().getId(), order2.getTotalAmount(), Instant.now()
        );

        // İlk event işlenir (Order 1 için Shipment oluşturulur)
        eventHandler.handle(event1);
        assertThat(shipmentRepository.findByOrderId(order1.getId())).isPresent();

        // Act: Aynı eventId fakat farklı orderId içeren ikinci event
        eventHandler.handle(event2);

        // Assert: Idempotency strictly eventId üzerinedir; Order 2 için Shipment OLUŞTURULMAMALIDIR.
        assertThat(shipmentRepository.findByOrderId(order2.getId())).isEmpty();
        assertThat(shipmentRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("6. Concurrency Test — İki ayrı thread aynı eventId ile eş zamanlı handle() çağırdığında yalnız 1 ProcessedEvent ve 1 Shipment oluşmalı")
    void shouldHandleConcurrentEventExecutionSafelyWithSingleShipment() throws InterruptedException {
        // Arrange
        Order order = createAndSaveOrder();
        UUID sharedEventId = UUID.randomUUID();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                sharedEventId, 1L, order.getId(), order.getCustomer().getId(), order.getTotalAmount(), Instant.now()
        );

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        // Thread-safe exception koleksiyonu
        ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();

        // Act
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    eventHandler.handle(event);
                } catch (Throwable t) {
                    // Thread üzerinde oluşan tüm hata/exception'ları yakalayıp kuyruğa aktarıyoruz
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Bütün thread'lerin hazır olduğu andan itibaren tetikleme veriliyor
        startLatch.countDown();

        // Her iki thread'in de işlemini bitirmesi bekleniyor (En fazla 5 saniye)
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert 1: İşlemler zaman aşımına uğramadan tamamlanmalı
        assertThat(completed).isTrue();

        // Assert 2: Thread'lerin hiçbirinde beklenmeyen bir Exception fırlatılmamış olmalı
        assertThat(exceptions)
                .withFailMessage("Concurrent execution caused unexpected exception(s): %s", exceptions)
                .isEmpty();

        // Assert 3: Veritabanında tam olarak 1 adet ProcessedEvent bulunmalı
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.findByEventId(sharedEventId.toString())).isPresent();

        // Assert 4: Veritabanında tam olarak 1 adet Shipment bulunmalı
        assertThat(shipmentRepository.count()).isEqualTo(1);
        Optional<Shipment> shipment = shipmentRepository.findByOrderId(order.getId());
        assertThat(shipment).isPresent();
        assertThat(shipment.get().getStatus()).isEqualTo(ShipmentStatus.READY);
    }
}