package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CheckoutRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutServiceImplIntegrationTest {

    @Autowired
    private CheckoutServiceImpl checkoutService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("Başarılı checkout senaryosunda sipariş, sipariş kalemleri ve adresler DB'ye persist edilmeli; stoklar düşmeli ve sepet temizlenmelidir")
    void checkout_WhenRequestIsValid_ShouldPersistOrderUpdateStockAndClearCartInDatabase() {
        // --- GIVEN ---
        // 1. Gerçek Müşteri Persist Ediliyor
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        customer = customerRepository.save(customer);

        // 2. Gerçek Ürünler Persist Ediliyor
        Product productA = productRepository.save(Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1"));
        Product productB = productRepository.save(Product.create("Kablosuz Mouse", new BigDecimal("50.00"), 5, "Açıklama 2"));

        // 3. Müşteriye Ait Sepet Oluşturuluyor ve Ürünler Ekleniyor
        Cart cart = new Cart(customer);
        cart.addProduct(productA, 2); // 2 * 100.00 = 200.00 TL
        cart.addProduct(productB, 1); // 1 * 50.00 = 50.00 TL
        cartRepository.save(cart);

        // 4. Checkout İsteği
        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // --- WHEN ---
        // Spring CGLIB Proxy üzerinden transactional metod çağrısı yapılıyor
        OrderResponse response = checkoutService.checkout(checkoutRequest, customer.getId());

        // --- THEN / DATABASE VERIFICATIONS ---

        // 1. Dönen Response Doğrulaması
        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));

        // 2. Veritabanından Siparişin ve İlişkili Varoluşların Yüklenmesi
        Order savedOrder = orderRepository.findById(response.id())
                .orElseThrow(() -> new AssertionError("Sipariş veritabanında bulunamadı!"));

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(savedOrder.getCustomerFirstName()).isEqualTo("Caner");
        assertThat(savedOrder.getCustomerLastName()).isEqualTo("Demir");
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("caner@example.com");
        assertThat(savedOrder.getCustomerPhone()).isEqualTo("+905551112233");
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));

        // Adres Snapshot Doğrulamaları
        assertThat(savedOrder.getShippingAddress()).isNotNull();
        assertThat(savedOrder.getShippingAddress().getCity()).isEqualTo("İstanbul");
        assertThat(savedOrder.getShippingAddress().getDistrict()).isEqualTo("Kadıköy");
        assertThat(savedOrder.getShippingAddress().getZipCode()).isEqualTo("34000");

        assertThat(savedOrder.getBillingAddress()).isNotNull();
        assertThat(savedOrder.getBillingAddress().getCity()).isEqualTo("İstanbul");
        assertThat(savedOrder.getBillingAddress().getDistrict()).isEqualTo("Ataşehir");
        assertThat(savedOrder.getBillingAddress().getZipCode()).isEqualTo("34100");

        // OrderItem Snapshot Doğrulamaları
        List<OrderItem> orderItems = savedOrder.getItems();
        assertThat(orderItems).hasSize(2);

        OrderItem itemA = orderItems.stream()
                .filter(item -> item.getProductId().equals(productA.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(itemA.getProductName()).isEqualTo("Klavyeli Kılıf");
        assertThat(itemA.getQuantity()).isEqualTo(2);
        assertThat(itemA.getUnitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(itemA.getLineTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(itemA.getDiscountAmount()).isEqualTo(BigDecimal.ZERO);

        OrderItem itemB = orderItems.stream()
                .filter(item -> item.getProductId().equals(productB.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(itemB.getProductName()).isEqualTo("Kablosuz Mouse");
        assertThat(itemB.getQuantity()).isEqualTo(1);
        assertThat(itemB.getUnitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(itemB.getLineTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(itemB.getDiscountAmount()).isEqualTo(BigDecimal.ZERO);

        // 3. Stok Güncellemelerinin Veritabanında Doğrulanması (10 - 2 = 8, 5 - 1 = 4)
        Product updatedProductA = productRepository.findById(productA.getId()).orElseThrow();
        Product updatedProductB = productRepository.findById(productB.getId()).orElseThrow();

        assertThat(updatedProductA.getStock()).isEqualTo(8);
        assertThat(updatedProductB.getStock()).isEqualTo(4);

        // 4. Sepet Durumunun Veritabanında Doğrulanması (Cart DB'de var, items boş)
        Cart updatedCart = cartRepository.findByCustomer_Id(customer.getId()).orElseThrow();
        assertThat(updatedCart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("Sipariş veritabanına kaydedilirken Hata oluşursa transaction rollback olmalı; stoklar ve sepet ilk haline dönmelidir")
    void checkout_WhenOrderPersistenceFails_ShouldRollbackAllDatabaseChanges() {
        // --- GIVEN ---
        // 1. Initial State: Customer, Product (Stock: 10) ve Cart (Item Qty: 2)
        Customer customer = customerRepository.save(
                new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password")
        );

        Product product = productRepository.save(
                Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1")
        );

        Cart cart = new Cart(customer);
        cart.addProduct(product, 2); // 2 * 100.00 = 200.00 TL
        cartRepository.save(cart);

        long initialOrderCount = orderRepository.count();

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // 2. DB Level Constraint Invalidation (Sadece bu test aksatılmak üzere total_amount < 0 şartı konuluyor)
        jdbcTemplate.execute("ALTER TABLE orders ADD CONSTRAINT check_total_amount_rollback_test CHECK (total_amount < 0)");

        try {
            // --- WHEN / THEN ---
            // Order persist aşamasında DataIntegrityViolationException / ConstraintViolationException fırlatılmalı
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> checkoutService.checkout(checkoutRequest, customer.getId())
            );

            // --- DATABASE ROLLBACK VERIFICATIONS ---

            // 1. Order Tablosu Doğrulaması: Hiçbir yeni sipariş eklenmemiş olmalı (Count aynı kalmalı)
            assertThat(orderRepository.count()).isEqualTo(initialOrderCount);

            // 2. Product Stok Doğrulaması: Düşürülen stok (8) ROLLBACK olarak tekrar ilk haline (10) dönmeli
            Product rolledBackProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(rolledBackProduct.getStock()).isEqualTo(10);

            // 3. Cart Doğrulaması: Temizlenen sepet (cart.clear()) ROLLBACK olarak tekrar 2 ürünü de barındırmalı
            Cart rolledBackCart = cartRepository.findByCustomer_Id(customer.getId()).orElseThrow();
            assertThat(rolledBackCart.getItems()).hasSize(1);
            assertThat(rolledBackCart.getItems().iterator().next().getQuantity()).isEqualTo(2);

        } finally {
            // Test sonrası DB temizliği: Constraint kaldırılıyor ki diğer testleri etkilemesin
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT check_total_amount_rollback_test");
        }
    }

    @Test
    @DisplayName("Aynı ürüne eşzamanlı checkout isteklerinde Pessimistic Lock stok yarışını önlemeli, biri başarılı diğeri InsufficientStockException almalıdır")
    void checkout_WhenConcurrentCheckoutRequests_ShouldHandleStockRaceConditionWithPessimisticLock() throws InterruptedException {
        // --- GIVEN ---
        // 1. İki Farklı Müşteri
        Customer customerA = customerRepository.save(
                new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password")
        );
        Customer customerB = customerRepository.save(
                new Customer("Ahmet", "Yılmaz", "ahmet@example.com", "+905552223344", "encoded_password")
        );

        // 2. Ortak Ürün (Stok: 5)
        Product productX = productRepository.save(
                Product.create("Stoklu Ürün", new BigDecimal("100.00"), 5, "Açıklama")
        );

        // 3. Her iki müşterinin sepetine 4'er adet ekleniyor (Toplam talep: 8, Stok: 5)
        Cart cartA = new Cart(customerA);
        cartA.addProduct(productX, 4);
        cartRepository.save(cartA);

        Cart cartB = new Cart(customerB);
        cartB.addProduct(productX, 4);
        cartRepository.save(cartB);

        AddressRequest addressReq = new AddressRequest("Ev", "İstanbul", "Kadıköy", "34000", "Türkiye", "Cadde No:1", "D:1");
        CheckoutRequest checkoutRequest = new CheckoutRequest(addressReq, addressReq);

        // Concurrency Kontrol Yapıları & Sayaçlar
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        try {
            // --- WHEN ---
            // İki checkout thread'inin aynı concurrency penceresinde başlatılması sağlanıyor
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    checkoutService.checkout(checkoutRequest, customerA.getId());
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    endLatch.countDown();
                }
            });

            executorService.submit(() -> {
                try {
                    startLatch.await();
                    checkoutService.checkout(checkoutRequest, customerB.getId());
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    endLatch.countDown();
                }
            });

            // Tetikleyici kapıyı aç
            startLatch.countDown();

            // Thread'lerin tamamlanması bekleniyor ve timeout/zaman aşımı kontrol ediliyor
            boolean completedInTime = endLatch.await(5, TimeUnit.SECONDS);
            assertThat(completedInTime)
                    .as("Her iki checkout thread'i de verilen timeout süresi içerisinde tamamlanmalıdır")
                    .isTrue();

        } finally {
            // Executor her senaryoda (assertion hatası dahil) güvenli şekilde kapatılıyor
            executorService.shutdownNow();
        }

        // --- THEN / VERIFY ---

        // 1. Thread Başarı ve Exception Sayacı Doğrulaması (1 Success + 1 InsufficientStockException)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.getFirst()).isInstanceOf(com.example.ordersystem.exception.InsufficientStockException.class);

        // 2. Stok Doğrulaması (5 - 4 = 1)
        Product updatedProduct = productRepository.findById(productX.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(1);

        // 3. Veritabanındaki Tek Sipariş ve Müşteri İlişkisi Doğrulaması
        assertThat(orderRepository.count()).isEqualTo(1);
        Order createdOrder = orderRepository.findAll().getFirst();

        Long successfulCustomerId = createdOrder.getCustomer().getId();
        assertThat(successfulCustomerId)
                .as("Oluşan sipariş Customer A veya Customer B'den birine ait olmalıdır")
                .isIn(customerA.getId(), customerB.getId());

        // 4. Müşteri, Sipariş ve Sepet İlişkisinin Doğrudan Doğrulanması
        Cart updatedCartA = cartRepository.findByCustomer_Id(customerA.getId()).orElseThrow();
        Cart updatedCartB = cartRepository.findByCustomer_Id(customerB.getId()).orElseThrow();

        if (successfulCustomerId.equals(customerA.getId())) {
            // Kazanan Customer A: Cart A boş, Cart B 4 ürünle kalmalı
            assertThat(updatedCartA.getItems()).isEmpty();
            assertThat(updatedCartB.getItems()).hasSize(1);
            assertThat(updatedCartB.getItems().iterator().next().getQuantity()).isEqualTo(4);
        } else {
            // Kazanan Customer B: Cart B boş, Cart A 4 ürünle kalmalı
            assertThat(updatedCartB.getItems()).isEmpty();
            assertThat(updatedCartA.getItems()).hasSize(1);
            assertThat(updatedCartA.getItems().iterator().next().getQuantity()).isEqualTo(4);
        }
    }
}