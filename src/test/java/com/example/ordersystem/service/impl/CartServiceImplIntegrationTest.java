package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartItemResponse;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.entity.Cart;
import com.example.ordersystem.entity.CartItem;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.exception.CartItemNotFoundException;
import com.example.ordersystem.exception.CartNotFoundException;
import com.example.ordersystem.exception.ProductNotAvailableException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.CartRepository;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.CartService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@ActiveProfiles("test")
class CartServiceImplIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("getCart Integration Tests")
    class GetCartIntegrationTests {

        @Test
        @DisplayName("When cart exists with items, should calculate subtotal and totalItemCount correctly from database")
        void getCart_WhenCartExistsWithItems_ShouldReturnCalculatedCartResponse() {
            // Given: Müşteri ve aktif ürünler database'e kaydedilir
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "12345", "password2"));

            Product product1 = productRepository.save(Product.create("Laptop", new BigDecimal("1500.00"), 10, "laptop description"));
            Product product2 = productRepository.save(Product.create("Mouse", new BigDecimal("50.00"), 15, "Mouse description"));

            Cart cart = new Cart(customer);
            cart.addProduct(product1, 1); // 1500.00
            cart.addProduct(product2, 2); // 100.00

            Cart savedCart = cartRepository.save(cart);

            // DB ile persistence context senkronizasyonu için flush & clear
            entityManager.flush();
            entityManager.clear();

            // When
            CartResponse response = cartService.getCart(customer.getId());

            // Then
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.totalItemCount()).isEqualTo(3); // 1 + 2
            assertThat(response.items()).hasSize(2);

            // Item bazlı hesaplamalar ve mapping doğrulamaları
            CartItemResponse item1Response = response.items().stream()
                    .filter(item -> item.productId().equals(product1.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(item1Response.quantity()).isEqualTo(1);
            assertThat(item1Response.unitPrice()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(item1Response.subtotal()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(item1Response.productName()).isEqualTo("Laptop");

            CartItemResponse item2Response = response.items().stream()
                    .filter(item -> item.productId().equals(product2.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(item2Response.quantity()).isEqualTo(2);
            assertThat(item2Response.unitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(item2Response.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(item2Response.productName()).isEqualTo("Mouse");
        }

        @Test
        @DisplayName("When cart does not exist, should return empty response and verify no cart is created in database")
        void getCart_WhenCartDoesNotExist_ShouldReturnEmptyResponseWithoutSideEffects() {
            // Given: Cart'ı olmayan geçerli bir müşteri
            Customer customer = customerRepository.save(new Customer("Ahmet", "Yılmaz", "ahmet@example.com", "123456", "password1"));

            entityManager.flush();
            entityManager.clear();

            // When
            CartResponse response = cartService.getCart(customer.getId());

            // Then
            // 1. Response seviyesi doğrulama
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.totalItemCount()).isZero();

            // 2. Database seviyesinde side-effect-free kontrolü
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isEmpty();
        }

        @Test
        @DisplayName("When cart exists but has no items, should return CartResponse with cartId and empty items list")
        void getCart_WhenCartExistsWithNoItems_ShouldReturnCartResponseWithEmptyItems() {
            // Given: Kullanıcının henüz hiç ürün eklenmemiş boş bir sepeti var
            Customer customer = customerRepository.save(new Customer("Mehmet", "Sert", "mehmet@example.com", "1324", "password"));
            Cart emptyCart = cartRepository.save(new Cart(customer));

            entityManager.flush();
            entityManager.clear();

            // When
            CartResponse response = cartService.getCart(customer.getId());

            // Then
            // 1. Response seviyesi doğrulamaları: cartId null olmamalı
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(emptyCart.getId());
            assertThat(response.items()).isEmpty();
            assertThat(response.totalItemCount()).isZero();

            // 2. Database seviyesinde sepetin gerçekten var olduğu doğrulunur
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isPresent();
            assertThat(cartInDb.get().getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addItem Integration Tests")
    class AddItemIntegrationTests {

        @Test
        @DisplayName("When cart does not exist for customer, should lazily create Cart and CartItem in database on first mutation")
        void addItem_WhenCartDoesNotExist_ShouldLazilyCreateCartAndCartItemInDatabase() {
            // Given: Cart'ı olmayan müşteri ve aktif bir ürün
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product product = productRepository.save(Product.create("Kablosuz Kulaklık", new BigDecimal("750.00"), 52, "en iyisi"));

            Integer quantity = 2;
            AddCartItemRequest request = new AddCartItemRequest(product.getId(), quantity);

            entityManager.flush();
            entityManager.clear();

            // When
            CartResponse response = cartService.addItem(request, customer.getId());

            // Persistence Context senkronizasyonu: Dirty checking ile atılan insert/update SQL'lerini DB'ye push et ve L1 cache'i temizle
            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Doğrulamaları
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isNotNull();
            assertThat(response.totalItemCount()).isEqualTo(quantity);
            assertThat(response.items()).hasSize(1);

            CartItemResponse itemResponse = response.items().getFirst();
            assertThat(itemResponse.productId()).isEqualTo(product.getId());
            assertThat(itemResponse.quantity()).isEqualTo(quantity);
            assertThat(itemResponse.unitPrice()).isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(itemResponse.subtotal()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(itemResponse.productName()).isEqualTo("Kablosuz Kulaklık");

            // 2. Database Seviyesi Doğrulamalar (Ayrı sorgular ile DB durumu)
            Optional<Cart> cartInDbOpt = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDbOpt).isPresent();

            Cart cartInDb = cartInDbOpt.get();
            assertThat(cartInDb.getId()).isEqualTo(response.cartId());

            // Cart doğru müşteri ile ilişkili mi?
            assertThat(cartInDb.getCustomer()).isNotNull();
            assertThat(cartInDb.getCustomer().getId()).isEqualTo(customer.getId());

            // 1 adet CartItem oluşmuş mu ve doğruluğu ne durumda?
            assertThat(cartInDb.getItems()).hasSize(1);

            CartItem cartItemInDb = cartInDb.getItems().getFirst();
            assertThat(cartItemInDb.getId()).isNotNull();
            assertThat(cartItemInDb.getQuantity()).isEqualTo(quantity);

            // CartItem doğru Product ile ilişkili mi?
            assertThat(cartItemInDb.getProduct()).isNotNull();
            assertThat(cartItemInDb.getProduct().getId()).isEqualTo(product.getId());
            assertThat(cartItemInDb.getProduct().getPrice()).isEqualByComparingTo(new BigDecimal("750.00"));
        }

        @Test
        @DisplayName("When product already exists in cart, should increment quantity on existing CartItem without creating a new row")
        void addItem_WhenProductAlreadyInCart_ShouldIncrementQuantityOfExistingCartItem() {
            // Given: Mevcut bir sepet ve içinde 2 adet eklenmiş bir ürün
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product product = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1200.00"), 20, "çok mekanik"));

            Cart cart = new Cart(customer);
            cart.addProduct(product, 2); // Ilk miktar: 2
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            // Aynı üründen 3 adet daha ekleme isteği
            AddCartItemRequest request = new AddCartItemRequest(product.getId(), 3);

            // When
            CartResponse response = cartService.addItem(request, customer.getId());

            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Doğrulamaları
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.totalItemCount()).isEqualTo(5); // 2 + 3
            assertThat(response.items()).hasSize(1); // Tek bir kalem ürün olmalı

            CartItemResponse itemResponse = response.items().getFirst();
            assertThat(itemResponse.productId()).isEqualTo(product.getId());
            assertThat(itemResponse.quantity()).isEqualTo(5);
            assertThat(itemResponse.subtotal()).isEqualByComparingTo(new BigDecimal("6000.00")); // 5 * 1200.00
            assertThat(itemResponse.productName()).isEqualTo("Mekanik Klavye");

            // 2. Database Seviyesi Doğrulamalar (cart_items tablosunda 2. bir satır fırlatılmadığını kanıtlama)
            Optional<Cart> cartInDbOpt = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDbOpt).isPresent();

            Cart cartInDb = cartInDbOpt.get();
            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());

            // DB'deki items koleksiyon boyutu tam olarak 1 olmalı! (Yeni satır INSERT edilmedi, var olan UPDATE edildi)
            assertThat(cartInDb.getItems()).hasSize(1);

            CartItem cartItemInDb = cartInDb.getItems().getFirst();
            assertThat(cartItemInDb.getProduct().getId()).isEqualTo(product.getId());
            assertThat(cartItemInDb.getQuantity()).isEqualTo(5);

            // DB seviyesinde toplam Cart ve CartItem sayılarını doğrulama
            assertThat(cartRepository.count()).isEqualTo(1);

            // Native/JPQL sorgusu ile DB'deki direkt cart_items tablosunu doğrula
            Long totalCartItemsCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .getSingleResult();
            assertThat(totalCartItemsCount).isEqualTo(1L);
        }

        @Test
        @DisplayName("When product is inactive and cart does not exist, should throw ProductNotAvailableException and ensure no cart is created")
        void addItem_WhenProductIsInactiveAndCartDoesNotExist_ShouldThrowExceptionAndNotCreateCart() {
            // Given: Cart'ı olmayan bir müşteri ve pasif (satışa kapalı) bir ürün
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product inactiveProduct = productRepository.save(Product.create("Eski Model Kulaklık", new BigDecimal("250.00"), 10, "çok eski, antika"));
            inactiveProduct.deactivate();

            AddCartItemRequest request = new AddCartItemRequest(inactiveProduct.getId(), 1);

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.addItem(request, customer.getId()))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(inactiveProduct.getId().toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Veritabanında hiçbir kayıt oluşmamalı)
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isEmpty();

            Long totalCartItemsCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci", Long.class)
                    .getSingleResult();
            assertThat(totalCartItemsCount).isZero();
        }

        @Test
        @DisplayName("When product does not exist and cart does not exist, should throw ProductNotFoundException and ensure no cart is created")
        void addItem_WhenProductDoesNotExist_ShouldThrowExceptionAndNotCreateCart() {
            // Given: Geçerli bir müşteri fakat DB'de var olmayan bir productId
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Long nonExistentProductId = 999999L;

            AddCartItemRequest request = new AddCartItemRequest(nonExistentProductId, 2);

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.addItem(request, customer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentProductId.toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isEmpty();

            Long totalCartItemsCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci", Long.class)
                    .getSingleResult();
            assertThat(totalCartItemsCount).isZero();
        }
    }

    @Nested
    @DisplayName("updateItem Integration Tests")
    class UpdateItemIntegrationTests {

        @Test
        @DisplayName("When updating item quantity, should update DB via JPA dirty checking without explicit repository save call")
        void updateItem_WhenValidRequest_ShouldUpdateQuantityInDbViaDirtyChecking() {
            // Given: Sepetinde 2 adet ürün olan bir müşteri
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product product = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "öyle"));

            Cart cart = new Cart(customer);
            cart.addProduct(product, 2); // Ilk miktar: 2
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5); // Yeni miktar: 5

            // When: Service katmanında miktar güncellenir (Explicit save() YOK!)
            CartResponse response = cartService.updateItem(product.getId(), customer.getId(), updateRequest);

            // Transaction / Persistence Context senkronizasyonu
            // Flush: Dirty checking sonucu oluşan UPDATE SQL'lerini DB'ye gönderir.
            // Clear: Persistence context'i temizleyerek sonraki okumaların managed
            //        entity state'ine bağlı kalmamasını sağlar.
            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Seviyesi Doğrulamalar
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.totalItemCount()).isEqualTo(5);
            assertThat(response.items()).hasSize(1);

            CartItemResponse itemResponse = response.items().getFirst();
            assertThat(itemResponse.productId()).isEqualTo(product.getId());
            assertThat(itemResponse.quantity()).isEqualTo(5);
            assertThat(itemResponse.subtotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(itemResponse.productName()).isEqualTo("Mekanik Klavye");

            // 2. Database Seviyesi Dirty Checking Doğrulaması (Dogrudan DB'den re-fetch edilerek)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);

            CartItem itemInDb = cartInDb.getItems().getFirst();
            assertThat(itemInDb.getQuantity()).isEqualTo(5); // DB'deki miktar 5 olarak guncellenmis olmali
        }

        @Test
        @DisplayName("When product exists in DB but not in cart, should throw CartItemNotFoundException and preserve existing cart state")
        void updateItem_WhenProductNotInCart_ShouldThrowExceptionAndPreserveCartState() {
            // Given: Müşteri, sepette mevcut olan Ürün A (2 adet) ve sepette OLMAYAN Ürün B
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "öyle"));
            Product productB = productRepository.save(Product.create("Oyuncu Mouse", new BigDecimal("500.00"), 11, "böyle"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2); // Sepette sadece Ürün A var (2 adet)
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.updateItem(productB.getId(), customer.getId(), updateRequest))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining(productB.getId().toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Sepetin mevcut durumu aynen korunmalı)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1); // Kalem sayısı hâlâ 1 olmalı

            CartItem existingItem = cartInDb.getItems().getFirst();
            assertThat(existingItem.getProduct().getId()).isEqualTo(productA.getId());
            assertThat(existingItem.getQuantity()).isEqualTo(2); // Ürün A'nın miktarı hâlâ 2 olmalı

            // JPQL ile DB seviyesinde Ürün B için hiç kayıt açılmadığını ve Ürün A'nın değişmediğini teyit etme
            Long countProductB = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .setParameter("productId", productB.getId())
                    .getSingleResult();
            assertThat(countProductB).isZero();
        }

        @Test
        @DisplayName("When updated product is inactive, should throw ProductNotAvailableException and preserve existing cart state")
        void updateItem_WhenProductIsInactive_ShouldThrowExceptionAndPreserveCartState() {
            // Given: Müşteri, sepette mevcut olan aktif Ürün A (2 adet) ve DB'de PASİF olan Ürün B
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product activeProductA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));
            Product inactiveProductB = productRepository.save(Product.create("Eski Monitör", new BigDecimal("3000.00"), 12, "b"));
            inactiveProductB.deactivate();

            Cart cart = new Cart(customer);
            cart.addProduct(activeProductA, 2); // Sepette sadece aktif Ürün A var (2 adet)
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);

            // When & Then
            // 1. Exception Seviyesi Doğrulama (Pasif ürün için öncelikli doğrulama hatası)
            assertThatThrownBy(() -> cartService.updateItem(inactiveProductB.getId(), customer.getId(), updateRequest))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(inactiveProductB.getId().toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Sepetin mevcut durumu aynen korunmalı)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1); // Kalem sayısı hâlâ 1 olmalı

            CartItem existingItem = cartInDb.getItems().getFirst();
            assertThat(existingItem.getProduct().getId()).isEqualTo(activeProductA.getId());
            assertThat(existingItem.getQuantity()).isEqualTo(2); // Ürün A'nın miktarı hâlâ 2 olmalı

            // Pasif Ürün B için bu operation sonucunda CartItem oluşmadığını teyit etme
            Long countProductB = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .setParameter("productId", inactiveProductB.getId())
                    .getSingleResult();
            assertThat(countProductB).isZero();
        }

        @Test
        @DisplayName("When cart does not exist for customer, should throw CartNotFoundException immediately without side-effects")
        void updateItem_WhenCartDoesNotExist_ShouldThrowCartNotFoundException() {
            // Given: DB'de kayıtlı müşteri ve ürün var; ancak müşteriye ait HİÇBİR Cart yok
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product product = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));

            UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(3);

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.updateItem(product.getId(), customer.getId(), updateRequest))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customer.getId().toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Sistemde hâlâ hiçbir Cart veya CartItem bulunmamalı)
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isEmpty();

            Long totalCartItemsCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci", Long.class)
                    .getSingleResult();
            assertThat(totalCartItemsCount).isZero();
        }

        @Test
        @DisplayName("When cart exists but product does not exist in DB, should throw ResourceNotFoundException and preserve cart state")
        void updateItem_WhenProductDoesNotExistInDb_ShouldThrowResourceNotFoundException() {
            // Given: Müşteri, 2 adet Ürün A içeren bir sepet ve DB'de HİÇ var olmayan bir productId
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2);
            Cart savedCart = cartRepository.save(cart);

            Long nonExistentProductId = 999999L;
            UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.updateItem(nonExistentProductId, customer.getId(), updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentProductId.toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Mevcut sepet durumu korunmalı)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new AssertionError("Cart DB'de bulunamadı!"));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);

            CartItem existingItem = cartInDb.getItems().getFirst();
            assertThat(existingItem.getProduct().getId()).isEqualTo(productA.getId());
            assertThat(existingItem.getQuantity()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("removeItem Integration Tests")
    class RemoveItemIntegrationTests {

        @Test
        @DisplayName("When valid request, should remove CartItem from database via JPA orphanRemoval")
        void removeItem_WhenValidRequest_ShouldRemoveCartItemFromDatabaseViaOrphanRemoval() {
            // Given: Müşteri ve sepetinde 2 farklı ürün (Ürün A: 2 adet, Ürün B: 3 adet)
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));
            Product productB = productRepository.save(Product.create("Oyuncu Mouse", new BigDecimal("500.00"), 12, "b"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2);
            cart.addProduct(productB, 3);
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            // When: Ürün A sepetten çıkarılır (Explicit delete/save çağrısı YOK!)
            CartResponse response = cartService.removeItem(productA.getId(), customer.getId());

            // Persistence context senkronizasyonu
            // flush(): orphanRemoval nedeniyle oluşan DELETE SQL'ini DB'ye gönderir.
            // clear(): Persistence Context'i temizleyerek L1 cache'teki entity'leri detach eder.
            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Seviyesi Doğrulamalar
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.items()).hasSize(1);
            assertThat(response.totalItemCount()).isEqualTo(3); // Sadece Ürün B (3 adet) kaldı

            CartItemResponse remainingItemResponse = response.items().getFirst();
            assertThat(remainingItemResponse.productId()).isEqualTo(productB.getId());
            assertThat(remainingItemResponse.quantity()).isEqualTo(3);
            assertThat(remainingItemResponse.subtotal()).isEqualByComparingTo(new BigDecimal("1500.00"));
            assertThat(remainingItemResponse.productName()).isEqualTo(productB.getName());

            // 2. Database Seviyesi Orphan Removal Doğrulaması (Aggregate Root üzerinden)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);
            assertThat(cartInDb.getItems().getFirst().getProduct().getId()).isEqualTo(productB.getId());

            // 3. JPQL/Direct Query Seviyesinde Silinme Doğrulaması (Orphan Removal Kanıtı)
            Long productACartItemCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .setParameter("productId", productA.getId())
                    .getSingleResult();

            assertThat(productACartItemCount).isZero(); // Product A'ya ait CartItem tablodan tamamen SILINMIŞ olmalı!
        }

        @Test
        @DisplayName("When cart does not exist for customer, should throw CartNotFoundException")
        void removeItem_WhenCartDoesNotExist_ShouldThrowCartNotFoundException() {
            // Given: DB'de kayıtlı müşteri ve ürün var; ancak müşteriye ait HİÇBİR Cart yok
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product product = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));

            entityManager.flush();
            entityManager.clear();

            // When & Then
            assertThatThrownBy(() -> cartService.removeItem(product.getId(), customer.getId()))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customer.getId().toString());

            entityManager.flush();
            entityManager.clear();

            // DB Side-effect doğrulaması
            assertThat(cartRepository.findByCustomer_Id(customer.getId())).isEmpty();
        }

        @Test
        @DisplayName("When product is inactive, should still successfully remove it from cart without throwing ProductNotAvailableException")
        void removeItem_WhenProductIsInactive_ShouldSuccessfullyRemoveItemFromCart() {
            // Given: Müşteri ve sepetinde pasifleşmiş bir ürün (Ürün A) ile aktif bir ürün (Ürün B)
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product inactiveProductA = productRepository.save(Product.create("Eski Klavye", new BigDecimal("500.00"), 10, "a")); // PASİF
            inactiveProductA.deactivate();
            Product activeProductB = productRepository.save(Product.create("Yeni Mouse", new BigDecimal("750.00"), 5, "b"));

            Cart cart = new Cart(customer);
            cart.addProduct(inactiveProductA, 1);
            cart.addProduct(activeProductB, 2);
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            // When: Pasif olan Ürün A sepetten çıkarılır
            CartResponse response = cartService.removeItem(inactiveProductA.getId(), customer.getId());

            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Seviyesi Doğrulamalar
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.items()).hasSize(1);
            assertThat(response.totalItemCount()).isEqualTo(2); // Sadece Ürün B (2 adet) kaldı

            CartItemResponse remainingItem = response.items().getFirst();

            assertThat(remainingItem.productId()).isEqualTo(activeProductB.getId());
            assertThat(remainingItem.productName()).isEqualTo(activeProductB.getName());
            assertThat(remainingItem.quantity()).isEqualTo(2);
            assertThat(remainingItem.unitPrice())
                    .isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(remainingItem.subtotal())
                    .isEqualByComparingTo(new BigDecimal("1500.00"));

            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);
            assertThat(cartInDb.getItems().getFirst().getProduct().getId())
                    .isEqualTo(activeProductB.getId());

            // 2. Database Seviyesi Orphan Removal Doğrulaması
            // Pasif ürünün CartItem kaydı veritabanından silinmiş olmalı.
            Long inactiveProductCartItemCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.product.id = :productId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .setParameter("productId", inactiveProductA.getId())
                    .getSingleResult();

            assertThat(inactiveProductCartItemCount).isZero();
        }

        @Test
        @DisplayName("When product does not exist in DB, should throw ResourceNotFoundException and preserve cart state")
        void removeItem_WhenProductDoesNotExistInDb_ShouldThrowResourceNotFoundException() {
            // Given: Müşteri, sepetinde 2 adet Ürün A ve DB'de var olmayan bir productId
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2);
            Cart savedCart = cartRepository.save(cart);

            Long nonExistentProductId = 999999L;

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.removeItem(nonExistentProductId, customer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(nonExistentProductId.toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Sepet durumu aynen korunmalı)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new CartNotFoundException(customer.getId()));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);
            assertThat(cartInDb.getItems().getFirst().getProduct().getId()).isEqualTo(productA.getId());
            assertThat(cartInDb.getItems().getFirst().getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("When product exists in DB but not in cart, should throw CartItemNotFoundException and preserve cart state")
        void removeItem_WhenProductNotInCart_ShouldThrowCartItemNotFoundException() {
            // Given: Müşteri, sepetinde Ürün A (2 adet) ve DB'de var olan ama sepette OLMAYAN Ürün B
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));
            Product productB = productRepository.save(Product.create("Oyuncu Mouse", new BigDecimal("500.00"), 9, "c"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2); // Sepette sadece Ürün A var
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            // When & Then
            // 1. Exception Seviyesi Doğrulama
            assertThatThrownBy(() -> cartService.removeItem(productB.getId(), customer.getId()))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining(productB.getId().toString());

            // Persistence context senkronizasyonu
            entityManager.flush();
            entityManager.clear();

            // 2. Database Side-Effect Verification (Sepet ve Ürün A kalemleri değişmeden kalmalı)
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new AssertionError("Cart DB'de bulunamadı!"));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId());
            assertThat(cartInDb.getItems()).hasSize(1);

            CartItem existingItem = cartInDb.getItems().getFirst();
            assertThat(existingItem.getProduct().getId()).isEqualTo(productA.getId());
            assertThat(existingItem.getQuantity()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("clearCart Integration Tests")
    class ClearCartIntegrationTests {

        @Test
        @DisplayName("When cart exists with multiple items, should remove all CartItems via orphanRemoval but preserve Cart entity")
        void clearCart_WhenCartExistsWithMultipleItems_ShouldRemoveAllItemsButKeepCart() {
            // Given: Müşteri ve sepetinde 3 farklı ürün (A: 2 adet, B: 3 adet, C: 1 adet)
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));
            Product productA = productRepository.save(Product.create("Mekanik Klavye", new BigDecimal("1000.00"), 10, "a"));
            Product productB = productRepository.save(Product.create("Oyuncu Mouse", new BigDecimal("500.00"), 32, "b"));
            Product productC = productRepository.save(Product.create("Kulaklık", new BigDecimal("750.00"), 21, "c"));

            Cart cart = new Cart(customer);
            cart.addProduct(productA, 2);
            cart.addProduct(productB, 3);
            cart.addProduct(productC, 1);
            Cart savedCart = cartRepository.save(cart);

            entityManager.flush();
            entityManager.clear();

            // When: Sepet boşaltılır (Explicit cartItemRepository.deleteAll() veya cartRepository.delete() YOK!)
            CartResponse response = cartService.clearCart(customer.getId());

            // Persistence Context senkronizasyonu
            // clear() sonrası CartItem'lar orphan durumuna gelir.
            // flush() sırasında orphanRemoval = true nedeniyle ilgili DELETE SQL'leri DB'ye gönderilir.
            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Seviyesi Doğrulamalar
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isEqualTo(savedCart.getId());
            assertThat(response.items()).isEmpty();
            assertThat(response.totalItemCount()).isZero();

            // 2. Database Seviyesi Cart Varlığı ve Immutability Doğrulaması
            Cart cartInDb = cartRepository.findByCustomer_Id(customer.getId())
                    .orElseThrow(() -> new AssertionError("Cart DB'de bulunamadı! Cart entity silinmiş olamaz."));

            assertThat(cartInDb.getId()).isEqualTo(savedCart.getId()); // Cart ID aynen korunmalı
            assertThat(cartInDb.getItems()).isEmpty(); // Sepet kalem koleksiyonu boş olmalı

            // 3. JPQL/Direct Query Seviyesinde Orphan Removal Doğrulaması
            Long cartItemCountForThisCart = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId", Long.class)
                    .setParameter("cartId", savedCart.getId())
                    .getSingleResult();

            assertThat(cartItemCountForThisCart).isZero(); // Bu sepete ait tüm CartItem'lar DB'den silinmiş olmalı
        }

        @Test
        @DisplayName("When cart does not exist for customer, should return empty CartResponse without creating a cart or throwing exception")
        void clearCart_WhenCartDoesNotExist_ShouldReturnEmptyResponseAndNotCreateCart() {
            // Given: DB'de kayıtlı bir müşteri var; ancak müşteriye ait HİÇBİR Cart kaydı yok
            Customer customer = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "135", "password"));

            entityManager.flush();
            entityManager.clear();

            // When: Olmayan sepet için clearCart çağrılır
            CartResponse response = cartService.clearCart(customer.getId());

            entityManager.flush();
            entityManager.clear();

            // Then
            // 1. Response Seviyesi Doğrulamalar (Graceful Null-Safe Fallback)
            assertThat(response).isNotNull();
            assertThat(response.cartId()).isNull(); // Cart hiç olmadığı için null olmalı
            assertThat(response.items()).isEmpty();
            assertThat(response.totalItemCount()).isZero();

            // 2. Database Side-Effect Verification (Veritabanında hâlâ hiç Cart veya CartItem oluşmamış olmalı)
            Optional<Cart> cartInDb = cartRepository.findByCustomer_Id(customer.getId());
            assertThat(cartInDb).isEmpty();
            assertThat(cartRepository.count()).isZero();

            Long totalCartItemsCount = entityManager
                    .createQuery("SELECT COUNT(ci) FROM CartItem ci", Long.class)
                    .getSingleResult();
            assertThat(totalCartItemsCount).isZero();
        }
    }
}