package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CheckoutRequest;
import com.example.ordersystem.dto.response.AddressResponse;
import com.example.ordersystem.dto.response.OrderItemResponse;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.*;
import com.example.ordersystem.mapper.AddressMapper;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CartRepository;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private CheckoutServiceImpl checkoutService;

    @Test
    @DisplayName("Başarılı checkout senaryosunda tüm domain kuralları, stok düşüşü, adres aktarımı ve sepet temizliği doğrulanmalıdır")
    void checkout_WhenRequestIsValid_ShouldCreateOrderDecreaseStockClearCartAndReturnResponse() {
        // --- GIVEN ---
        Long customerId = 1L;

        // 1. Customer Setup
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        // 2. Address & Request Setup
        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        Address shippingAddressEntity = new Address("Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5");
        Address billingAddressEntity = new Address("İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3");

        when(addressMapper.toEntity(shippingAddressReq)).thenReturn(shippingAddressEntity);
        when(addressMapper.toEntity(billingAddressReq)).thenReturn(billingAddressEntity);

        // 3. Product Setup (Product.create fabrika metodu ve Reflection ID ataması)
        Product product1 = Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1");
        ReflectionTestUtils.setField(product1, "id", 10L);

        Product product2 = Product.create("Kablosuz Mouse", new BigDecimal("50.00"), 5, "Açıklama 2");
        ReflectionTestUtils.setField(product2, "id", 20L);

        // 4. Cart & CartItems Setup
        Cart cart = new Cart(customer);
        ReflectionTestUtils.setField(cart, "id", 100L);

        cart.addProduct(product1, 2); // Total P1: 2 * 100.00 = 200.00 TL
        cart.addProduct(product2, 1); // Total P2: 1 * 50.00 = 50.00 TL

        // 5. Repository Mocks
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInWithLock(Set.of(10L, 20L)))
                .thenReturn(List.of(product1, product2));

        // 6. Order Repository & OrderResponse Stubs
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse shippingResponse = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5");
        AddressResponse billingResponse = new AddressResponse("İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3");

        OrderItemResponse itemResp1 = new OrderItemResponse(100L, 10L, "Klavyeli Kılıf", 2, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("200.00"));
        OrderItemResponse itemResp2 = new OrderItemResponse(101L, 20L, "Kablosuz Mouse", 1, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"));

        OrderResponse expectedResponse = new OrderResponse(
                500L,
                Instant.parse("2026-10-04T10:00:00Z"),
                OrderStatus.PENDING,
                customerId,
                new BigDecimal("250.00"),
                List.of(itemResp1, itemResp2),
                shippingResponse,
                billingResponse
        );

        when(orderMapper.toOrderResponse(any(Order.class))).thenReturn(expectedResponse);

        // --- WHEN ---
        OrderResponse actualResponse = checkoutService.checkout(checkoutRequest, customerId);

        // --- THEN / VERIFY ---

        // 1. Dönen response doğrulaması
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(expectedResponse);

        // 2. Stok azalmalarının doğrulanması (Product1: 10 - 2 = 8, Product2: 5 - 1 = 4)
        assertThat(product1.getStock()).isEqualTo(8);
        assertThat(product2.getStock()).isEqualTo(4);

        // 3. Sepetin temizlendiğinin doğrulanması (cart.clear())
        verify(cart, times(1)).clear();
        assertThat(cart.getItems()).isEmpty();

        // 4. Order Aggregate state ve snapshot doğrulamaları
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();

        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(capturedOrder.getCustomer()).isEqualTo(customer);
        assertThat(capturedOrder.getCustomerFirstName()).isEqualTo("Caner");
        assertThat(capturedOrder.getCustomerLastName()).isEqualTo("Demir");
        assertThat(capturedOrder.getCustomerEmail()).isEqualTo("caner@example.com");
        assertThat(capturedOrder.getCustomerPhone()).isEqualTo("+905551112233");

        // Adreslerin Order'a doğru aktarıldığı
        assertThat(capturedOrder.getShippingAddress()).isEqualTo(shippingAddressEntity);
        assertThat(capturedOrder.getBillingAddress()).isEqualTo(billingAddressEntity);

        // OrderItem detayları ve Fiyat Hesaplama
        assertThat(capturedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(capturedOrder.getItems()).hasSize(2);

        OrderItem capturedItem1 = capturedOrder.getItems().stream()
                .filter(item -> item.getProductId().equals(10L))
                .findFirst()
                .orElseThrow();

        assertThat(capturedItem1.getProductName()).isEqualTo("Klavyeli Kılıf");
        assertThat(capturedItem1.getQuantity()).isEqualTo(2);
        assertThat(capturedItem1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(capturedItem1.getLineTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(capturedItem1.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        OrderItem capturedItem2 = capturedOrder.getItems().stream()
                .filter(item -> item.getProductId().equals(20L))
                .findFirst()
                .orElseThrow();

        assertThat(capturedItem2.getProductName()).isEqualTo("Kablosuz Mouse");
        assertThat(capturedItem2.getQuantity()).isEqualTo(1);
        assertThat(capturedItem2.getUnitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(capturedItem2.getLineTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(capturedItem2.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Repository etkileşim doğrulamaları
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(10L, 20L));
        verify(orderMapper).toOrderResponse(capturedOrder);
        verify(addressMapper).toEntity(shippingAddressReq);
        verify(addressMapper).toEntity(billingAddressReq);
    }

    @Test
    @DisplayName("Müşteri bulunamadığında ResourceNotFoundException fırlatılmalı ve sonraki repository/mapper işlemleri çağrılmamalıdır")
    void checkout_WhenCustomerNotFound_ShouldThrowResourceNotFoundException() {
        // --- GIVEN ---
        Long customerId = 1L;

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        // --- WHEN / THEN ---
        assertThrows(
                ResourceNotFoundException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        // 1. Müşteri sorgusunun yapıldığı doğrulanır
        verify(customerRepository).findById(customerId);

        // 2. Müşteri bulunamadığı için akışın erkenden kesildiği (early failure) ve diğer hiçbir bileşene dokunulmadığı doğrulanır
        verifyNoInteractions(
                cartRepository,
                productRepository,
                orderRepository,
                addressMapper,
                orderMapper
        );
    }

    @Test
    @DisplayName("Müşterinin sepeti bulunamadığında CartNotFoundException fırlatılmalı ve sonraki repository/mapper işlemleri çağrılmamalıdır")
    void checkout_WhenCartNotFound_ShouldThrowResourceNotFoundException() {
        // --- GIVEN ---
        Long customerId = 1L;

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.empty());

        // --- WHEN / THEN ---
        assertThrows(
                CartNotFoundException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        // 1. Müşteri ve sepet sorgularının yapıldığı doğrulanır
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);

        // 2. Sepet bulunamadığı için akışın kesildiği ve sonraki bileşenlerin çağrılmadığı doğrulanır
        verifyNoInteractions(
                productRepository,
                orderRepository,
                addressMapper,
                orderMapper
        );
    }

    @Test
    @DisplayName("Sepet boş olduğunda EmptyCartException fırlatılmalı, ürün kilitleme aşamasına geçilmemeli ve sepet temizlenmemelidir")
    void checkout_WhenCartIsEmpty_ShouldThrowEmptyCartException() {
        // --- GIVEN ---
        Long customerId = 1L;

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // Boş sepet mock'lanıyor (spy ile clear çağrısının yapılmadığı doğrulanacak)
        Cart emptyCart = spy(new Cart(customer));
        ReflectionTestUtils.setField(emptyCart, "id", 100L);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(emptyCart));

        // --- WHEN / THEN ---
        assertThrows(
                EmptyCartException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        // 1. Müşteri ve sepet sorgularının yapıldığı doğrulanır
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);

        // 2. Başarısız checkout durumunda sepetin temizlenmediği (clear çağrılmadığı) doğrulanır
        verify(emptyCart, never()).clear();

        // 3. Ürün kilitleme, sipariş oluşturma ve mapper işlemlerine hiç geçilmediği doğrulanır
        verifyNoInteractions(
                productRepository,
                orderRepository,
                addressMapper,
                orderMapper
        );
    }

    @Test
    @DisplayName("Sepetteki ürünlerden biri veritabanında bulunamadığında ResourceNotFoundException fırlatılmalı, stok düşülmemeli ve sepet temizlenmemelidir")
    void checkout_WhenProductNotFound_ShouldThrowResourceNotFoundException() {
        // --- GIVEN ---
        Long customerId = 1L;

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // Product 10 DB'de mevcut, Product 20 DB'de silinmiş/bulunamıyor
        Product product10 = Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1");
        ReflectionTestUtils.setField(product10, "id", 10L);

        // Fake Product 20 (Sadece sepete ekleyebilmek için)
        Product product20Fake = Product.create("Kablosuz Mouse", new BigDecimal("50.00"), 5, "Açıklama 2");
        ReflectionTestUtils.setField(product20Fake, "id", 20L);

        // Sepette hem Product 10 hem Product 20 var
        Cart cart = spy(new Cart(customer));
        ReflectionTestUtils.setField(cart, "id", 100L);
        cart.addProduct(product10, 2);
        cart.addProduct(product20Fake, 1);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(cart));

        // DB'den kilitlenerek getirilen listede sadece Product 10 var (Product 20 eksik!)
        when(productRepository.findAllByIdInWithLock(Set.of(10L, 20L)))
                .thenReturn(List.of(product10));

        // --- WHEN / THEN ---
        assertThrows(
                ResourceNotFoundException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        // 1. Repository kilit sorgusunun yapıldığı doğrulanır
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(10L, 20L));

        // 2. Bulunan mevcut ürünün stoğunun DEĞİŞTİRİLMEDİĞİ doğrulanır (Stok kontrolüne geçilmeden Hata fırlatıldı)
        assertThat(product10.getStock()).isEqualTo(10);

        // 3. Sepetin temizlenmediği doğrulanır
        verify(cart, never()).clear();
        assertThat(cart.getItems()).hasSize(2);

        // 4. Sipariş kaydı ve Mapper işlemlerine geçilmediği doğrulanır
        verifyNoInteractions(
                orderRepository,
                addressMapper,
                orderMapper
        );
    }

    @Test
    @DisplayName("Sepetteki ürün pasif (INACTIVE) durumdaysa stoğu yeterli olsa bile ProductNotAvailableException fırlatılmalı, stok düşülmemeli ve sipariş oluşturulmamalıdır")
    void checkout_WhenProductIsInactive_ShouldThrowProductNotAvailableException() {
        // --- GIVEN ---
        Long customerId = 1L;

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // Stoğu yeterli (10) fakat durumu INACTIVE olan ürün
        Product inactiveProduct = Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1");
        ReflectionTestUtils.setField(inactiveProduct, "id", 10L);
        ReflectionTestUtils.setField(inactiveProduct, "status", ProductStatus.PASSIVE);

        Cart cart = spy(new Cart(customer));
        ReflectionTestUtils.setField(cart, "id", 100L);
        cart.addProduct(inactiveProduct, 2);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInWithLock(Set.of(10L)))
                .thenReturn(List.of(inactiveProduct));

        // --- WHEN / THEN ---
        assertThrows(
                ProductNotAvailableException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(10L));

        // 1. Stok yeterli olmasına rağmen pasif olduğu için stok düşümünün HİÇ yapılmadığı doğrulanır
        assertThat(inactiveProduct.getStock()).isEqualTo(10);

        // 2. Sepetin temizlenmediği doğrulanır
        verify(cart, never()).clear();
        assertThat(cart.getItems()).hasSize(1);

        // 3. Sipariş oluşturma ve mapper işlemlerine geçilmediği doğrulanır
        verifyNoInteractions(
                orderRepository,
                addressMapper,
                orderMapper
        );
    }

    @Test
    @DisplayName("Sepetteki ürün miktarı mevcut stoktan fazla olduğunda InsufficientStockException fırlatılmalı, stok düşülmemeli ve sipariş kaydedilmemelidir")
    void checkout_WhenInsufficientStock_ShouldThrowInsufficientStockException() {
        // --- GIVEN ---
        Long customerId = 1L;

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password");
        ReflectionTestUtils.setField(customer, "id", customerId);

        AddressRequest shippingAddressReq = new AddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34000", "Türkiye", "Atatürk Cad. No:10", "Daire 5"
        );
        AddressRequest billingAddressReq = new AddressRequest(
                "İş Adresi", "İstanbul", "Ataşehir", "34100", "Türkiye", "İnönü Cad. No:20", "Kat 3"
        );
        CheckoutRequest checkoutRequest = new CheckoutRequest(shippingAddressReq, billingAddressReq);

        // Durumu ACTIVE ancak stoğu (3) yetersiz ürün
        Product activeProduct = Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 3, "Açıklama 1");
        ReflectionTestUtils.setField(activeProduct, "id", 10L);

        Cart cart = spy(new Cart(customer));
        ReflectionTestUtils.setField(cart, "id", 100L);
        // Sepete stoktan fazla (5 adet) ekleniyor
        cart.addProduct(activeProduct, 5);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(cartRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInWithLock(Set.of(10L)))
                .thenReturn(List.of(activeProduct));

        // --- WHEN / THEN ---
        assertThrows(
                InsufficientStockException.class,
                () -> checkoutService.checkout(checkoutRequest, customerId)
        );

        // --- VERIFY ---
        verify(customerRepository).findById(customerId);
        verify(cartRepository).findByCustomer_Id(customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(10L));

        // 1. Stok yetersizliği nedeniyle stok düşümünün gerçekleşmediği (stokun 3 olarak kaldığı) doğrulanır
        assertThat(activeProduct.getStock()).isEqualTo(3);

        // 2. Sepetin temizlenmediği doğrulanır
        verify(cart, never()).clear();
        assertThat(cart.getItems()).hasSize(1);

        // 3. Sipariş oluşturma ve mapper işlemlerine geçilmediği doğrulanır
        verifyNoInteractions(
                orderRepository,
                addressMapper,
                orderMapper
        );
    }
}