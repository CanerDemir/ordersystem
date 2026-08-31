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
import com.example.ordersystem.exception.OrderCannotBeCancelledException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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

    @Autowired
    EntityManager entityManager;

    Instant createdAt = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    @DisplayName("Integration Test 1: Order save sırasında DB constraint ihlali olursa tüm işlem ve stoklar rollback edilmeli")
    void transaction_whenExceptionOccurs_shouldRollbackStockChanges() {
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

    @Test
    @DisplayName("Get Order Integration Test 1: Müşteri (Customer A) kendi sipariş detayını PostgreSQL'den başarıyla çekebilmeli")
    void getOrderDetail_whenCustomerRequestsOwnOrder_shouldReturnOrderDetailWithAllRelationships() {
        Customer customerA = customerRepository.save(
                new Customer("Customer", "A", "customera@example.com", "5551111111", "pass123")
        );

        Product product = new Product("Test Laptop", BigDecimal.valueOf(1500), 10, "Best laptop", ProductStatus.ACTIVE, Instant.now(), null);
        product = productRepository.save(product);

        AddressRequest addressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2"
        );
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                addressReq,
                addressReq
        );

        CurrentUser currentUserA = new CurrentUser(customerA.getId());

        Long orderIdForCustomerA = orderService.createOrder(createOrderRequest, currentUserA).id();

        OrderResponse response = orderService.getOrderById(orderIdForCustomerA, currentUserA);

        assertNotNull(response);
        assertEquals(orderIdForCustomerA, response.id());
        assertEquals(customerA.getId(), response.customerId());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.totalAmount()));

        assertNotNull(response.shippingAddress());
        assertEquals("Ev Adresi", response.shippingAddress().title());
        assertEquals("İstanbul", response.shippingAddress().city());
        assertEquals("Kadıköy",  response.shippingAddress().district());
        assertEquals("34710", response.shippingAddress().zipCode());
        assertEquals("Türkiye", response.shippingAddress().country());
        assertEquals("Moda Cad. No:1", response.shippingAddress().addressLine());
        assertEquals("D 2",  response.shippingAddress().addressDetail());

        assertNotNull(response.billingAddress());
        assertEquals("Ev Adresi", response.billingAddress().title());
        assertEquals("İstanbul", response.billingAddress().city());
        assertEquals("Kadıköy",  response.billingAddress().district());
        assertEquals("34710", response.billingAddress().zipCode());
        assertEquals("Türkiye", response.billingAddress().country());
        assertEquals("Moda Cad. No:1", response.billingAddress().addressLine());
        assertEquals("D 2",  response.billingAddress().addressDetail());

        assertEquals(1, response.items().size());
        assertEquals("Test Laptop", response.items().getFirst().productName());
        assertEquals(1, response.items().getFirst().quantity());
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.items().getFirst().unitPrice()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.items().getFirst().lineTotal()));
    }

    @Test
    @DisplayName("Get Order Integration Test 2 (IDOR Protection): Customer B, Customer A'ya ait siparişi sorguladığında PostgreSQL seviyesinde 0 satır dönmeli ve ResourceNotFoundException fırlatılmalı")
    void getOrderDetail_whenCustomerRequestsAnotherCustomersOrder_shouldThrowResourceNotFoundException() {
        Customer customerA = customerRepository.save(
                new Customer("Customer", "A", "customera@example.com", "5551111111", "pass123")
        );
        Customer customerB = customerRepository.save(
                new Customer("Customer", "B", "customerb@example.com", "5552222222", "pass123")
        );

        Product product = new Product("Test Laptop", BigDecimal.valueOf(1500), 10, "Best laptop", ProductStatus.ACTIVE, Instant.now(), null);
        product = productRepository.save(product);

        AddressRequest addressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2"
        );
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                List.of(new OrderItemRequest(product.getId(), 1)),
                addressReq,
                addressReq
        );

        CurrentUser currentUserA = new CurrentUser(customerA.getId());
        CurrentUser currentUserB = new CurrentUser(customerB.getId());

        Long orderIdForCustomerA = orderService.createOrder(createOrderRequest, currentUserA).id();

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getOrderById(orderIdForCustomerA, currentUserB),
                "Başka bir müşterinin siparişi sorgulandığında ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(String.valueOf(orderIdForCustomerA)));
    }

    @Test
    @DisplayName("Cancel Order Integration Test 1 (Happy Path): İptal edilen PENDING siparişin statüsü CANCELLED olmalı ve stoklar veritabanında güncellenmeli (Product A: 10->12, Product B: 20->23)")
    void cancelOrder_shouldRestoreStockAndSetStatusToCancelledInPostgres() {
        Customer customer = new Customer("Caner", "Demir", "caner.integration@example.com", "5551234567", "password123");
        entityManager.persist(customer);

        Product productA = new Product("Product A", BigDecimal.valueOf(100), 10, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        entityManager.persist(productA);
        Product productB = new Product("Product B", BigDecimal.valueOf(50), 20, "Product B desc.", ProductStatus.ACTIVE, createdAt, null);
        entityManager.persist(productB);

        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("350.00"), createdAt);
        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"), BigDecimal.valueOf(150));
        order.addOrderItem(itemA);
        order.addOrderItem(itemB);
        entityManager.persist(order);

        entityManager.flush();
        entityManager.clear();

        Long orderId = order.getId();
        Long customerId = customer.getId();
        Long productAId = productA.getId();
        Long productBId = productB.getId();

        CurrentUser  currentUser = new CurrentUser(customerId);

        OrderResponse response = orderService.cancelOrder(orderId, currentUser);

        entityManager.flush();
        entityManager.clear();

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, response.status());

        Order updatedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        assertEquals(OrderStatus.CANCELLED, updatedOrder.getStatus(), "Veritabanındaki sipariş statüsü CANCELLED olmalıdır.");

        Product updatedProductA = productRepository.findById(productAId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productAId));
        assertEquals(12, updatedProductA.getStock(), "Product A stoğu PostgreSQL'de 10'dan 12'ye yükselmiş olmalıdır.");

        Product updatedProductB = productRepository.findById(productBId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productBId));
        assertEquals(23, updatedProductB.getStock(), "Product B stoğu PostgreSQL'de 20'den 23'e yükselmiş olmalıdır.");
    }

    @Test
    @DisplayName("Cancel Order Integration Test 2 (IDOR Protection): Müşteri B, Müşteri A'nın siparişini iptal etmeye çalıştığında ResourceNotFoundException fırlatılmalı ve DB verileri değişmemeli")
    void cancelOrder_whenUserAttemptsToCancelAnotherUsersOrder_shouldThrowExceptionAndKeepDbStateUnchanged() {
        Customer customerA = new Customer("Customer", "A", "customer.a@example.com", "5551112233", "pass123");
        entityManager.persist(customerA);

        Customer customerB = new Customer("Customer", "B", "customer.b@example.com", "5554445566", "pass123");
        entityManager.persist(customerB);

        Product product = new Product("Product A", BigDecimal.valueOf(100), 15, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        entityManager.persist(product);

        // 4. Customer A'ya ait PENDING Sipariş (Ürün miktarı: 3)
        Order orderA = new Order(OrderStatus.PENDING, customerA, customerA.getPhone(), customerA.getFirstName(), customerA.getLastName(), customerA.getEmail(), new BigDecimal("300.00"), createdAt);
        OrderItem item = new OrderItem(product.getId(), product.getName(), 3, new BigDecimal("100.00"), BigDecimal.valueOf(300));
        orderA.addOrderItem(item);
        entityManager.persist(orderA);

        entityManager.flush();
        entityManager.clear();

        Long orderAId = orderA.getId();
        Long customerBId = customerB.getId();
        Long productId = product.getId();

        CurrentUser currentUserB = new CurrentUser(customerBId);

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.cancelOrder(orderAId, currentUserB),
                "Başka müşterinin siparişi iptal edilmek istendiğinde ResourceNotFoundException fırlatılmalıdır."
        );

        entityManager.clear();

        Order unchangedOrder = orderRepository.findById(orderAId)
                .orElseThrow(() -> new ResourceNotFoundException("Order",  orderAId));
        assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus(), "Yetkisiz erişimde sipariş statüsü PENDING kalmalıdır.");

        Product unchangedProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product",  productId));
        assertEquals(15, unchangedProduct.getStock(), "Yetkisiz erişimde ürün stoğu artmamalı, 15 olarak kalmalıdır.");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED"})
    @DisplayName("Cancel Order Integration Test 3: Statüsü CANCELLED olan sipariş tekrar iptal edilmeye çalışıldığında OrderCannotBeCancelledException fırlatılmalı ve DB stoğu değişmemeli")
    void cancelOrder_whenOrderIsNotPending_shouldThrowExceptionAndKeepStockUnchanged(OrderStatus initialStatus) {
        Customer customer = new Customer("Caner", "Demir", "caner.nonpending@example.com", "5551234567", "pass123");
        entityManager.persist(customer);

        // Başlangıç Stok Miktarı: 10
        Product product = new Product("Test Product", BigDecimal.valueOf(100), 10, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        entityManager.persist(product);

        Order order = new Order(initialStatus, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("200.00"), createdAt);
        OrderItem item = new OrderItem(product.getId(), product.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        order.addOrderItem(item);
        entityManager.persist(order);

        entityManager.flush();
        entityManager.clear();

        Long orderId = order.getId();
        Long customerId = customer.getId();
        Long productId = product.getId();

        CurrentUser currentUser = new CurrentUser(customerId);

        assertThrows(
                OrderCannotBeCancelledException.class,
                () -> orderService.cancelOrder(orderId, currentUser),
                "PENDING olmayan sipariş iptal edilmeye çalışıldığında OrderCannotBeCancelledException fırlatılmalıdır."
        );

        entityManager.clear();

        Order unchangedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order",   orderId));
        assertEquals(initialStatus, unchangedOrder.getStatus(), "Sipariş statüsü değişmeden ilk değerini korumalıdır.");

        Product unchangedProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product",  productId));
        assertEquals(10, unchangedProduct.getStock(), "Başarısız iptal denemesinde veritabanındaki ürün stoğu değişmemelidir.");
    }

    @Test
    @DisplayName("Cancel Order Integration Test 4: İptal edilen siparişteki ürün DB'den silinmişse ResourceNotFoundException fırlatılmalı ve sipariş statüsü ROLLBACK edilerek PENDING kalmalı")
    void cancelOrder_whenProductDoesNotExistInDb_shouldThrowResourceNotFoundExceptionAndRollback() {
        Customer customer = new Customer("Caner", "Demir", "caner.missingprod@example.com", "5551234567", "pass123");
        entityManager.persist(customer);

        Product product = new Product("Silinecek Ürün", BigDecimal.valueOf(100), 5, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        entityManager.persist(product);

        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("200.00"), createdAt);
        OrderItem item = new OrderItem(product.getId(), product.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        order.addOrderItem(item);
        entityManager.persist(order);

        entityManager.flush();

        Long orderId = order.getId();
        Long customerId = customer.getId();
        Long productId = product.getId();

        entityManager.createNativeQuery("DELETE FROM products WHERE id = :productId")
                .setParameter("productId", productId)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        CurrentUser currentUser = new CurrentUser(customerId);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.cancelOrder(orderId, currentUser),
                "DB'de ürün bulunamadığında ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(String.valueOf(productId)));

        entityManager.clear();

        Order unchangedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order",  orderId));

        assertEquals(OrderStatus.PENDING, unchangedOrder.getStatus(), "Ürün bulunamadığında yapılan işlem rollback olmalı ve sipariş PENDING kalmalıdır.");
    }

    @Test
    @DisplayName("Cancel Order Integration Test 5 (Transaction Rollback): İptal adımlarında RuntimeException oluştuğunda tüm DB değişiklikleri rollback olmalı, stoklar ve statü eski haline dönmeli")
    void cancelOrder_whenExceptionOccurs_shouldRollbackAllDatabaseChanges() {
        Customer customer = new Customer("Caner", "Demir", "caner.rollback@example.com", "5551234567", "pass123");
        customer = customerRepository.save(customer);

        Product productA = new Product("Product A", BigDecimal.valueOf(100), 10, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        productA = productRepository.save(productA);

        // Product B (Başlangıç Stok: 20)
        Product productB = new Product("Product B", BigDecimal.valueOf(50), 20, "Product B desc.", ProductStatus.ACTIVE, createdAt, null);
        productB = productRepository.save(productB);

        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("1150.00"), createdAt);
        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 10, new BigDecimal("100.00"), BigDecimal.valueOf(1000.00));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"),  BigDecimal.valueOf(150.00));
        order.addOrderItem(itemA);
        order.addOrderItem(itemB);
        order = orderRepository.save(order);

        entityManager.createNativeQuery(
                "ALTER TABLE products ADD CONSTRAINT check_max_stock CHECK (stock_quantity <= 15)"
        ).executeUpdate();

        Long orderId = order.getId();
        Long customerId = customer.getId();
        Long productAId = productA.getId();
        Long productBId = productB.getId();

        CurrentUser currentUser = new CurrentUser(customerId);

        try {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> orderService.cancelOrder(orderId, currentUser),
                    "PostgreSQL kısıtlaması ihlal edildiğinde veritabanı hata fırlatmalıdır."
            );
            Order rollbackOrder = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order",  orderId));
            assertEquals(OrderStatus.PENDING, rollbackOrder.getStatus(), "Rollback sonrası sipariş statüsü PENDING kalmalıdır.");

            Product rollbackProductA = productRepository.findById(productAId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product",   productAId));
            assertEquals(10, rollbackProductA.getStock(), "Rollback sonrası Product A stoğu eski değeri olan 10 olarak kalmalıdır.");

            Product rollbackProductB = productRepository.findById(productBId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product B", productBId));
            assertEquals(20, rollbackProductB.getStock(), "Rollback sonrası Product B stoğu eski değeri olan 20 olarak kalmalıdır.");
        } finally {
            entityManager.createNativeQuery("ALTER TABLE products DROP CONSTRAINT IF EXISTS check_max_stock").executeUpdate();
        }
    }

    @Test
    @DisplayName("Cancel Order Integration Test 6 (Concurrent Cancel): Aynı sipariş için iki eşzamanlı iptal isteğinde sadece 1'i başarılı olmalı, stock 14 olmalı ve diğer thread OrderCannotBeCancelledException almalı")
    void cancelOrder_concurrentRequests_shouldProcessOnlyOneSuccessfullyAndKeepStockConsistent() throws InterruptedException {
        Customer customer = new Customer("Caner", "Demir", "caner.concurrent@example.com", "5551234567", "pass123");
        customer = customerRepository.save(customer);

        // Initial Stock = 10
        Product product = new Product("Concurrent Test Product", BigDecimal.valueOf(100), 10, "Product A desc.", ProductStatus.ACTIVE, createdAt, null);
        product = productRepository.save(product);

        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("400.00"), createdAt);
        OrderItem item = new OrderItem(product.getId(), product.getName(), 4, new BigDecimal("100.00"), BigDecimal.valueOf(400.00));
        order.addOrderItem(item);
        order = orderRepository.save(order);

        Long orderId = order.getId();
        Long customerId = customer.getId();
        Long productId = product.getId();

        CurrentUser currentUser = new CurrentUser(customerId);

        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1); // Her iki thread'in tam olarak aynı anda başlamasını sağlar
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads); // Thread'lerin bitmesini bekler

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Diğer thread hazır olana kadar bekle
                    orderService.cancelOrder(orderId, currentUser);
                    successCount.incrementAndGet();
                } catch (OrderCannotBeCancelledException e) {
                    exceptionCount.incrementAndGet();
                    caughtException.set(e);
                } catch (Throwable t) {
                    caughtException.set(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completedInTime = finishLatch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completedInTime, "Thread'ler kilit takılması (deadlock) yaşamadan makul sürede tamamlanmalıdır.");

        assertEquals(1, successCount.get(), "Tam olarak 1 iptal isteği başarılı olmalıdır.");
        assertEquals(1, exceptionCount.get(), "Tam olarak 1 iptal isteği OrderCannotBeCancelledException fırlatmalıdır.");
        assertNotNull(caughtException.get(), "Eşzamanlı isteğin biri hataya düşmelidir.");
        assertInstanceOf(OrderCannotBeCancelledException.class, caughtException.get(), "Fırlatılan hata OrderCannotBeCancelledException olmalıdır.");

        Product finalProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product B", productId));
        assertEquals(14, finalProduct.getStock(), "Ürün stoğu tam olarak 14 olmalıdır (10 + 4). Çift restorasyon (race condition) engellenmiştir.");

        Order finalOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order B", orderId));
        assertEquals(OrderStatus.CANCELLED, finalOrder.getStatus(), "Sipariş statüsü CANCELLED olmalıdır.");
    }
}
