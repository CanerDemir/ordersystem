package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.request.OrderItemRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class OrderServiceImplIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);

        // H2 veya in-memory DB yerine PostgreSQL dialect kullanıldığını teyit edin
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update"); // Veya flyway/liquibase kullanıyorsanız validate
    }

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Integration Test 1: Order save sırasında DB constraint ihlali olursa tüm işlem ve stoklar rollback edilmeli")
    void transaction_whenExceptionOccurs_shouldRollbackStockChanges() {
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer = customerRepository.save(customer);

        AddressRequest addressRequest = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4"
        );

        Product product1 = new Product("Laptop", BigDecimal.valueOf(250), 10, "Best laptop", ProductStatus.ACTIVE, Instant.now(), null);
        Product product2 = new Product("Mouse",  BigDecimal.valueOf(100), 10, "Best mouse", ProductStatus.ACTIVE, Instant.now(), null);
        product1 = productRepository.save(product1);
        product2 = productRepository.save(product2);

        Long product1Id = product1.getId();
        Long product2Id = product2.getId();
        Long beforeOrderCount = orderRepository.count();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        Product p1 = productRepository.findById(product1Id).orElseThrow();
                        Product p2 = productRepository.findById(product2Id).orElseThrow();

                        p1.decreaseStock(2);
                        p2.decreaseStock(3);

                        productRepository.save(p1);
                        productRepository.save(p2);

                        throw new DataIntegrityViolationException(("Simulated unexpected error right after stock deduction!"));
                    });
                }
        );

        assertEquals(beforeOrderCount, orderRepository.count(), "Rollback sonrası veritabanında hiçbir Order kaydı olmamalıdır.");

        Product reloadedProduct1 = productRepository.findById(product1Id).orElseThrow();
        assertEquals(10, reloadedProduct1.getStock(), "Laptop stoku rollback sonrası ilk değerine (10) dönmelidir.");

        Product reloadedProduct2 = productRepository.findById(product2Id).orElseThrow();
        assertEquals(10, reloadedProduct2.getStock(), "Mouse stoku rollback sonrası ilk değerine (10) dönmelidir.");
    }

    @Test
    @DisplayName("Integration Test 2 (Happy Path): Başarılı sipariş akışında PostgreSQL state'i ve yanıt eksiksiz doğrulanmalı")
    void createOrder_whenRequestIsValid_shouldPersistOrderAndItemsAndDeductStockInDatabase() {
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer = customerRepository.save(customer);

        Product productA = new Product("Product A", BigDecimal.valueOf(100), 10, "Best mouse", ProductStatus.ACTIVE, Instant.now(), null);
        Product productB = new Product("Product B", BigDecimal.valueOf(250), 20, "Best laptop", ProductStatus.ACTIVE, Instant.now(), null);
        productA = productRepository.save(productA);
        productB = productRepository.save(productB);

        Long customerId = customer.getId();
        Long productAId = productA.getId();
        Long productBId = productB.getId();
        Long beforeOrderCount = orderRepository.count();

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "Fatura Adresi", "Ankara", "Çankaya", "06540", "Türkiye", "Atatürk Bulvarı No:100", "Kat 2"
        );

        OrderItemRequest item1Request = new OrderItemRequest(productAId, 2);
        OrderItemRequest item2Request = new OrderItemRequest(productBId, 3);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1Request, item2Request),
                shippingAddressReq,
                billingAddressReq
        );

        CurrentUser currentUser = new CurrentUser(customerId);
        OrderResponse response = orderService.createOrder(request, currentUser);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(customerId, response.customerId());
        assertEquals(0, new BigDecimal("950.00").compareTo(response.totalAmount()));
        assertEquals(2, response.items().size());
        assertEquals(beforeOrderCount + 1, orderRepository.count(), "CreateOrder sonrası DB'deki order sayısı bir artmış olmalı.");

        Order persistedOrder = orderRepository.findById(response.id()).orElseThrow();
        assertEquals(OrderStatus.PENDING, persistedOrder.getStatus());
        assertEquals(customerId, persistedOrder.getCustomer().getId());
        assertEquals(0, new BigDecimal("950.00").compareTo(persistedOrder.getTotalAmount()));

        List<OrderItem> persistedItems = persistedOrder.getItems();

        assertNotNull(persistedItems);
        assertEquals(2, persistedItems.size());

        OrderItem persistedItemA = persistedItems.stream()
                .filter(item -> item.getProductId().equals(productAId))
                .findFirst()
                .orElseThrow();

        assertEquals(2, persistedItemA.getQuantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(persistedItemA.getUnitPrice()));
        assertEquals(0, new BigDecimal("200.00").compareTo(persistedItemA.getLineTotal()));
        assertEquals("Product A", persistedItemA.getProductName());

        OrderItem persistedItemB = persistedItems.stream()
                .filter(item -> item.getProductId().equals(productBId))
                .findFirst()
                .orElseThrow();

        assertEquals(3, persistedItemB.getQuantity());
        assertEquals(0, new BigDecimal("250.00").compareTo(persistedItemB.getUnitPrice()));
        assertEquals(0, new BigDecimal("750.00").compareTo(persistedItemB.getLineTotal()));
        assertEquals("Product B", persistedItemB.getProductName());

        Address shippingAddress = persistedOrder.getShippingAddress();
        assertNotNull(shippingAddress);
        assertEquals("Ev Adresi", shippingAddress.getTitle());
        assertEquals("İstanbul", shippingAddress.getCity());
        assertEquals("Kadıköy", shippingAddress.getDistrict());
        assertEquals("34710", shippingAddress.getZipCode());
        assertEquals("Türkiye", shippingAddress.getCountry());
        assertEquals("Caferağa Mah. Moda Cad. No:12", shippingAddress.getAddressLine());
        assertEquals("Daire 4", shippingAddress.getAddressDetail());

        // Billing Address DB Kontrolü
        Address billingAddress = persistedOrder.getBillingAddress();
        assertNotNull(billingAddress);
        assertEquals("Fatura Adresi", billingAddress.getTitle());
        assertEquals("Ankara", billingAddress.getCity());
        assertEquals("Çankaya", billingAddress.getDistrict());
        assertEquals("06540", billingAddress.getZipCode());
        assertEquals("Türkiye", billingAddress.getCountry());
        assertEquals("Atatürk Bulvarı No:100", billingAddress.getAddressLine());
        assertEquals("Kat 2", billingAddress.getAddressDetail());

        Product reloadedProductA = productRepository.findById(productAId).orElseThrow();
        assertEquals(8, reloadedProductA.getStock(), "Product A stoku veritabanında 10 -> 8 olarak güncellenmiş olmalı");

        Product reloadedProductB = productRepository.findById(productBId).orElseThrow();
        assertEquals(17, reloadedProductB.getStock(), "Product B stoku veritabanında 20 -> 17 olarak güncellenmiş olmalı");
    }

    @Test
    @DisplayName("Integration Test 3: Yetersiz stok durumunda InsufficientStockException fırlamalı ve PostgreSQL state'i tamamen korunmalı")
    void createOrder_whenStockIsInsufficient_shouldThrowExceptionAndRollbackAllChanges() {
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer = customerRepository.save(customer);

        Product productA = new Product("Product A", BigDecimal.valueOf(100), 10, "Best mouse", ProductStatus.ACTIVE, Instant.now(), null);
        Product productB = new Product("Product B", BigDecimal.valueOf(250), 2, "Best laptop", ProductStatus.ACTIVE, Instant.now(), null);
        productA = productRepository.save(productA);
        productB = productRepository.save(productB);

        Long customerId = customer.getId();
        Long productAId = productA.getId();
        Long productBId = productB.getId();
        Long beforeOrderCount = orderRepository.count();

        AddressRequest addressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2"
        );

        OrderItemRequest item1Request = new OrderItemRequest(productAId, 2);
        OrderItemRequest item2Request = new OrderItemRequest(productBId, 5); // 5 > 2 (Hata tetikleyecek)

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1Request, item2Request),
                addressReq,
                addressReq
        );

        CurrentUser currentUser = new CurrentUser(customerId);

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request, currentUser),
                "Yetersiz stok durumunda InsufficientStockException fırlatılmalıdır."
        );
        assertTrue(exception.getMessage().contains(String.valueOf(productBId)));

        Product reloadedProductA = productRepository.findById(productAId).orElseThrow();
        assertEquals(10, reloadedProductA.getStock(), "Product A stoku rollback sonrasında orijinal değeri olan 10 kalmalıdır.");
        Product reloadedProductB = productRepository.findById(productBId).orElseThrow();
        assertEquals(2, reloadedProductB.getStock(), "Product B stoku orijinal değeri olan 2 kalmalıdır.");

        assertEquals(beforeOrderCount, orderRepository.count());
    }

    @Test
    @DisplayName("Integration Test 4: Eşzamanlı 2 sipariş isteğinde Pessimistic Lock overselling'i engellemeli")
    void createOrder_whenConcurrentOrdersRequestMoreThanAvailableStock_shouldPreventOversellingWithPessimisticLock() throws InterruptedException {
        Customer customerA = customerRepository.save(
                new Customer("Müşteri", "A", "customera@example.com", "5551111111", "pass123")
        );
        Customer customerB = customerRepository.save(
                new Customer("Müşteri", "B", "customerb@example.com", "5552222222", "pass123")
        );

        Product productA = new Product("Concurrent Target Product", BigDecimal.valueOf(100), 5, "Best mouse", ProductStatus.ACTIVE, Instant.now(), null);
        productA = productRepository.save(productA);

        Long productTargetId = productA.getId();
        Long customerAId = customerA.getId();
        Long customerBId = customerB.getId();
        Long beforeOrderCount = orderRepository.count();

        AddressRequest addressReq = new AddressRequest(
                "Adres", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2"
        );

        CreateOrderRequest requestForCustomerA = new CreateOrderRequest(
                List.of(new OrderItemRequest(productTargetId, 4)),
                addressReq,
                addressReq
        );

        CreateOrderRequest requestForCustomerB = new CreateOrderRequest(
                List.of(new OrderItemRequest(productTargetId, 4)),
                addressReq,
                addressReq
        );

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger failedOrders = new AtomicInteger(0);
        AtomicReference<Throwable> capturedException = new AtomicReference<>();

        CurrentUser currentUserA = new CurrentUser(customerAId);
        CurrentUser currentUserB = new CurrentUser(customerBId);

        executorService.submit(() -> {
            try {
                startGate.await();
                orderService.createOrder(requestForCustomerA, currentUserA);
                successfulOrders.incrementAndGet();
            } catch (Exception e) {
                failedOrders.incrementAndGet();
                capturedException.set(e);
            } finally {
                endGate.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                startGate.await();
                orderService.createOrder(requestForCustomerB, currentUserB);
                successfulOrders.incrementAndGet();
            } catch (Exception e) {
                failedOrders.incrementAndGet();
                capturedException.set(e);
            } finally {
                endGate.countDown();
            }
        });
        startGate.countDown();
        endGate.await();
        executorService.shutdown();

        assertEquals(1, successfulOrders.get(), "Sadece 1 sipariş başarılı olmalıdır.");
        assertEquals(1, failedOrders.get(), "Sadece 1 sipariş başarısız olmalıdır.");

        assertInstanceOf(
                InsufficientStockException.class,
                capturedException.get(),
                "Elenen transaction InsufficientStockException fırlatmalıdır."
        );

        Product reloadedProduct = productRepository.findById(productTargetId).orElseThrow();
        assertEquals(1, reloadedProduct.getStock(), "Başlangıç stoku (5) - Başarılı sipariş (4) = Kalan stok 1 olmalıdır.");

        assertEquals(beforeOrderCount + 1, orderRepository.count(), "PostgreSQL'de yalnızca 1 adet yeni Order kaydedilmiş olmalıdır.");
    }
}
