package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.request.OrderItemRequest;
import com.example.ordersystem.dto.response.AddressResponse;
import com.example.ordersystem.dto.response.OrderItemResponse;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.DuplicateProductInOrderException;
import com.example.ordersystem.exception.InsufficientStockException;
import com.example.ordersystem.exception.ProductNotAvailableException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

    @Captor
    private ArgumentCaptor<Order> orderArgumentCaptor;

    Instant createdAt = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    @DisplayName("Test 1: Başarılı order senaryosu - Stoklar düşmeli, sipariş adresiyle birlikte kaydedilmeli ve yanıt dönmeli")
    void createOrder_whenRequestIsValid_shouldCreateOrderAndDeductStock() {
        Long product1Id = 100L;
        Long product2Id = 101L;

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        CreateOrderRequest request = getCreateOrderRequest(product1Id, product2Id);

        Product product1 = createProduct(product1Id, "Laptop", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(50));
        Product product2 = createProduct(product2Id, "Mouse", ProductStatus.ACTIVE, 20, BigDecimal.valueOf(100));
        when(productRepository.findAllByIdInWithLock(Set.of(product1Id, product2Id))).thenReturn(List.of(product1, product2));

        Order savedOrder = mock(Order.class);
        when(savedOrder.getId()).thenReturn(1000L);
        when(savedOrder.getCustomer()).thenReturn(customer);

        AddressResponse addressResponse = new AddressResponse(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4"
        );
        OrderItemResponse item1Response = new OrderItemResponse(1L, product1Id, "Ürün 1", 2, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(100));
        OrderItemResponse item2Response = new OrderItemResponse(2L, product2Id, "Ürün 2", 5, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(500));
        OrderResponse expectedResponse = new OrderResponse(
                1000L,
                createdAt,
                OrderStatus.PENDING,
                customer.getId(),
                BigDecimal.valueOf(600),
                List.of(item1Response, item2Response),
                addressResponse,
                addressResponse
        );

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toOrderResponse(savedOrder)).thenReturn(expectedResponse);

        CurrentUser currentUser = new CurrentUser(customer.getId());
        OrderResponse actualResponse = orderServiceImpl.createOrder(request, currentUser);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(product1).decreaseStock(2);
        verify(product2).decreaseStock(5);

        verify(orderRepository, times(1)).save(orderArgumentCaptor.capture());
        Order capturedOrder = orderArgumentCaptor.getValue();

        assertNotNull(capturedOrder);
        assertEquals(OrderStatus.PENDING, capturedOrder.getStatus());
        assertEquals(customer,  capturedOrder.getCustomer());
        assertEquals(0, BigDecimal.valueOf(600).compareTo(capturedOrder.getTotalAmount()));

        List<OrderItem> capturedItems = capturedOrder.getItems();
        assertNotNull(capturedItems);
        assertEquals(2, capturedItems.size());

        OrderItem capturedItem1 = capturedItems.stream()
                        .filter(item -> item.getProductId().equals(product1Id))
                            .findFirst()
                                .orElseThrow();
        assertEquals(product1Id, capturedItem1.getProductId());
        assertEquals("Laptop", capturedItem1.getProductName());
        assertEquals(2, capturedItem1.getQuantity());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(capturedItem1.getUnitPrice()));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(capturedItem1.getLineTotal()));

        OrderItem capturedItem2 = capturedItems.stream()
                        .filter(item -> item.getProductId().equals(product2Id))
                                .findFirst()
                                        .orElseThrow();
        assertEquals(product2Id, capturedItem2.getProductId());
        assertEquals("Mouse", capturedItem2.getProductName());
        assertEquals(5, capturedItem2.getQuantity());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(capturedItem2.getUnitPrice()));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(capturedItem2.getLineTotal()));

        Address capturedShippingAddress = capturedOrder.getShippingAddress();
        assertNotNull(capturedShippingAddress);
        assertEquals("Ev Adresi", capturedShippingAddress.getTitle());
        assertEquals("İstanbul",  capturedShippingAddress.getCity());
        assertEquals("Kadıköy", capturedShippingAddress.getDistrict());
        assertEquals("34710", capturedShippingAddress.getZipCode());
        assertEquals("Türkiye", capturedShippingAddress.getCountry());
        assertEquals("Caferağa Mah. Moda Cad. No:12", capturedShippingAddress.getAddressLine());
        assertEquals("Daire 4", capturedShippingAddress.getAddressDetail());

        Address capturedBillingAddress = capturedOrder.getBillingAddress();
        assertNotNull(capturedBillingAddress);
        assertEquals("Ev Adresi", capturedBillingAddress.getTitle());
        assertEquals("İstanbul",  capturedBillingAddress.getCity());
        assertEquals("Kadıköy", capturedBillingAddress.getDistrict());
        assertEquals("34710", capturedBillingAddress.getZipCode());
        assertEquals("Türkiye", capturedBillingAddress.getCountry());
        assertEquals("Caferağa Mah. Moda Cad. No:12", capturedBillingAddress.getAddressLine());
        assertEquals("Daire 4", capturedBillingAddress.getAddressDetail());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(product1Id, product2Id));
        verify(orderMapper).toOrderResponse(savedOrder);
    }

    @Test
    @DisplayName("Test 2: Customer bulunamadığında ResourceNotFoundException fırlatılmalı ve product erişimi yapılmamalı")
    void createOrder_whenCustomerNotFound_shouldThrowResourceNotFoundExceptionAndShouldNotAccessProducts() {
        Long nonExistingCustomerId = 999L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );
        OrderItemRequest itemRequest = new OrderItemRequest(100L, 2);
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(itemRequest), addressRequest, addressRequest
        );

        when(customerRepository.findById(nonExistingCustomerId)).thenReturn(Optional.empty());

        CurrentUser currentUser = new CurrentUser(nonExistingCustomerId);
        assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        verify(customerRepository).findById(nonExistingCustomerId);

        verifyNoInteractions(productRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 3: Request içinde mükerrer productId olduğunda DuplicateProductInOrderException fırlatılmalı ve product lock alınmamalı")
    void createOrder_whenDuplicateProductInRequest_shouldThrowDuplicateProductInOrderExceptionAndShouldNotAccessProducts() {
        Long duplicateProductId = 10L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );

        OrderItemRequest item1 = new OrderItemRequest(duplicateProductId, 2);
        OrderItemRequest item2 = new OrderItemRequest(duplicateProductId, 3);

        CreateOrderRequest request = new CreateOrderRequest(List.of(item1, item2), addressRequest, addressRequest);

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        assertThrows(
                DuplicateProductInOrderException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        verify(customerRepository).findById(customer.getId());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 4: Request'teki ürünlerden biri veritabanında bulunamadığında ResourceNotFoundException fırlatılmalı ve stok düşülmemeli")
    void createOrder_whenSomeProductsNotFound_shouldThrowResourceNotFoundExceptionAndNotDeductStock() {
        Long product10Id = 10L;
        Long product20Id = 20L;
        Long product30Id = 30L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );

        OrderItemRequest item1 = new OrderItemRequest(product10Id, 2);
        OrderItemRequest item2 = new OrderItemRequest(product20Id, 1);
        OrderItemRequest item3 = new OrderItemRequest(product30Id, 3);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1, item2, item3), addressRequest, addressRequest
        );

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Product product10 = createProduct(product10Id, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));
        Product product30 = createProduct(product30Id, "Product B", ProductStatus.ACTIVE, 15, BigDecimal.valueOf(150));

        when(productRepository.findAllByIdInWithLock(Set.of(product10Id, product20Id, product30Id))).thenReturn(List.of(product10, product30));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        assertTrue(exception.getMessage().contains(String.valueOf(product20Id)));

        verify(product10, never()).decreaseStock(anyInt());
        verify(product30, never()).decreaseStock(anyInt());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(product10Id, product20Id, product30Id));
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 5: Ürünlerden biri INACTIVE olduğunda ProductNotAvailableException fırlatılmalı ve stok düşülmemeli")
    void createOrder_whenProductIsNotActive_shouldThrowProductNotAvailableExceptionAndNotDeductStock() {
        Long activeProductId = 100L;
        Long inactiveProductId = 101L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );

        OrderItemRequest item1 = new OrderItemRequest(activeProductId, 2);
        OrderItemRequest item2 = new OrderItemRequest(inactiveProductId, 1);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1, item2), addressRequest, addressRequest
        );

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Product activeProduct = createProduct(activeProductId, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(50));
        Product inactiveProduct = createProduct(inactiveProductId, "Product B", ProductStatus.PASSIVE, 20, BigDecimal.valueOf(100));

        when(productRepository.findAllByIdInWithLock(Set.of(activeProductId, inactiveProductId))).thenReturn(List.of(activeProduct,  inactiveProduct));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        assertThrows(
                ProductNotAvailableException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        verify(activeProduct, never()).decreaseStock(anyInt());
        verify(inactiveProduct, never()).decreaseStock(anyInt());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(activeProductId, inactiveProductId));
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 6: İstenecek stok miktarı mevcut stoktan fazla olduğunda InsufficientStockException fırlatılmalı ve stok değiştirilmemeli")
    void createOrder_whenStockIsInsufficient_shouldThrowInsufficientStockExceptionAndNotChangeStock() {
        Long productId = 100L;
        Long insufficientProductId = 101L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );

        OrderItemRequest item = new OrderItemRequest(productId, 5);
        OrderItemRequest item2 = new OrderItemRequest(insufficientProductId, 5);

        CreateOrderRequest request = new CreateOrderRequest(List.of(item, item2), addressRequest, addressRequest);

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Product product = createProduct(productId, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));
        Product insufficientProduct = createProduct(insufficientProductId, "Product B", ProductStatus.ACTIVE, 2, BigDecimal.valueOf(100));

        when(productRepository.findAllByIdInWithLock(Set.of(productId, insufficientProductId))).thenReturn(List.of(product, insufficientProduct));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        assertThrows(
                InsufficientStockException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        verify(product, never()).decreaseStock(anyInt());
        verify(insufficientProduct, never()).decreaseStock(anyInt());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(productId, insufficientProductId));
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 7: Aynı istekte hem pasif ürün hem yetersiz stok olduğunda öncelikli olarak ProductNotAvailableException fırlatılmalı")
    void createOrder_whenBothInactiveAndInsufficientStock_shouldPrioritizeProductNotAvailableException() {
        Long inactiveProductId = 100L;
        Long insufficientProductId = 101L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah.", "Daire 4"
        );

        OrderItemRequest inactiveItem = new OrderItemRequest(inactiveProductId, 10);
        OrderItemRequest insufficientItem = new OrderItemRequest(insufficientProductId, 10);

        CreateOrderRequest request = new CreateOrderRequest(List.of(inactiveItem, insufficientItem), addressRequest, addressRequest);

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Product inactiveProduct = createProduct(inactiveProductId, "Product A", ProductStatus.PASSIVE, 20, BigDecimal.valueOf(100));
        Product insufficientProduct = createProduct(insufficientProductId, "Product B", ProductStatus.ACTIVE, 5, BigDecimal.valueOf(100));

        when(productRepository.findAllByIdInWithLock(Set.of(inactiveProductId, insufficientProductId))).thenReturn(List.of(inactiveProduct, insufficientProduct));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        assertThrows(
                ProductNotAvailableException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );

        verify(inactiveProduct, never()).decreaseStock(anyInt());
        verify(insufficientProduct, never()).decreaseStock(anyInt());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(inactiveProductId, insufficientProductId));
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Test 8: Başarılı Order - PENDING status, stok düşümü, adresler ve tüm OrderItem detayları ArgumentCaptor ile doğrulanmalı")
    void createOrder_whenRequestIsValid_shouldCreateOrderWithPendingStatusAndCorrectCalculations() {
        Long product100Id = 100L;
        Long product101Id = 101L;

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        AddressRequest shippingAddressReq = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4"
        );
        AddressRequest billingAddressReq = createAddressRequest(
                "Fatura Adresi", "Ankara", "Çankaya", "06540", "Türkiye", "Atatürk Bulvarı No:100", "Kat 2"
        );

        OrderItemRequest item1Request = new OrderItemRequest(product100Id, 2);
        OrderItemRequest item2Request = new OrderItemRequest(product101Id, 3);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1Request, item2Request),
                shippingAddressReq,
                billingAddressReq
        );

        Product product100 = createProduct(product100Id, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100.00));
        Product product101 = createProduct(product101Id, "Product B", ProductStatus.ACTIVE, 20, BigDecimal.valueOf(250.00));

        when(productRepository.findAllByIdInWithLock(Set.of(product100Id, product101Id)))
                .thenReturn(List.of(product100, product101));

        Order savedOrder = mock(Order.class);
        when(savedOrder.getId()).thenReturn(5000L);
        when(savedOrder.getCustomer()).thenReturn(customer);

        AddressResponse shippingAddrResp = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4");
        AddressResponse billingAddrResp = new AddressResponse("Fatura Adresi", "Ankara", "Çankaya", "06540", "Türkiye", "Atatürk Bulvarı No:100", "Kat 2");

        OrderItemResponse item1Resp = new OrderItemResponse(1L, product100Id, "Product A", 2, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("200.00"));
        OrderItemResponse item2Resp = new OrderItemResponse(2L, product101Id, "Product B", 3, new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("750.00"));

        OrderResponse expectedResponse = new OrderResponse(
                5000L,
                createdAt,
                OrderStatus.PENDING,
                customer.getId(),
                new BigDecimal("950.00"),
                List.of(item1Resp, item2Resp),
                shippingAddrResp,
                billingAddrResp
        );

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toOrderResponse(savedOrder)).thenReturn(expectedResponse);

        CurrentUser currentUser = new CurrentUser(customer.getId());
        OrderResponse actualResponse = orderServiceImpl.createOrder(request, currentUser);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(product100).decreaseStock(2);
        verify(product101).decreaseStock(3);

        verify(orderRepository).save(orderArgumentCaptor.capture());
        Order capturedOrder = orderArgumentCaptor.getValue();
        assertNotNull(capturedOrder);
        assertEquals(OrderStatus.PENDING, capturedOrder.getStatus());
        assertEquals(customer, capturedOrder.getCustomer());
        assertEquals(customer.getEmail(), capturedOrder.getCustomerEmail());
        assertEquals(customer.getPhone(), capturedOrder.getCustomerPhone());
        assertEquals(customer.getFirstName(), capturedOrder.getCustomerFirstName());
        assertEquals(customer.getLastName(), capturedOrder.getCustomerLastName());
        assertEquals(0, new BigDecimal("950.00").compareTo(capturedOrder.getTotalAmount()));

        List<OrderItem> capturedItems = capturedOrder.getItems();
        assertNotNull(capturedItems);
        assertEquals(2, capturedItems.size());

        OrderItem capturedItem100 =  capturedItems.stream()
                .filter(item -> item.getProductId().equals(product100Id))
                .findFirst()
                .orElseThrow();
        assertEquals(2, capturedItem100.getQuantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(capturedItem100.getUnitPrice()));
        assertEquals(0, new BigDecimal("200.00").compareTo(capturedItem100.getLineTotal()));

        OrderItem capturedItem101 =  capturedItems.stream()
                .filter(item -> item.getProductId().equals(product101Id))
                .findFirst()
                .orElseThrow();
        assertEquals(3, capturedItem101.getQuantity());
        assertEquals(0, new BigDecimal("250.00").compareTo(capturedItem101.getUnitPrice()));
        assertEquals(0, new BigDecimal("750.00").compareTo(capturedItem101.getLineTotal()));

        Address capturedShipping = capturedOrder.getShippingAddress();
        assertNotNull(capturedShipping);
        assertEquals(shippingAddressReq.title(), capturedShipping.getTitle());
        assertEquals(shippingAddressReq.city(), capturedShipping.getCity());
        assertEquals(shippingAddressReq.district(), capturedShipping.getDistrict());
        assertEquals(shippingAddressReq.zipCode(), capturedShipping.getZipCode());
        assertEquals(shippingAddressReq.country(), capturedShipping.getCountry());
        assertEquals(shippingAddressReq.addressLine(), capturedShipping.getAddressLine());
        assertEquals(shippingAddressReq.addressDetail(), capturedShipping.getAddressDetail());

        Address capturedBilling = capturedOrder.getBillingAddress();
        assertNotNull(capturedBilling);
        assertEquals(billingAddressReq.title(), capturedBilling.getTitle());
        assertEquals(billingAddressReq.city(), capturedBilling.getCity());
        assertEquals(billingAddressReq.district(), capturedBilling.getDistrict());
        assertEquals(billingAddressReq.zipCode(), capturedBilling.getZipCode());
        assertEquals(billingAddressReq.country(), capturedBilling.getCountry());
        assertEquals(billingAddressReq.addressLine(), capturedBilling.getAddressLine());
        assertEquals(billingAddressReq.addressDetail(), capturedBilling.getAddressDetail());

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(product100Id, product101Id));
        verify(orderMapper).toOrderResponse(savedOrder);
    }

    @Test
    @DisplayName("Test 9: orderRepository.save() RuntimeException fırlattığında servis hatayı dışarı atmalı ve mapper çalışmamalı")
    void createOrder_whenSaveFails_shouldPropagateExceptionAndNotCallMapper() {
        Long product1Id = 100L;
        Long product2Id = 200L;

        AddressRequest addressRequest = createAddressRequest(
                "Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Caferağa Mah. Moda Cad. No:12", "Daire 4"
        );

        OrderItemRequest item1Request = new OrderItemRequest(product1Id, 2);
        OrderItemRequest item2Request = new OrderItemRequest(product2Id, 3);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(item1Request, item2Request),
                addressRequest,
                addressRequest
        );

        Customer customer = createCustomer();
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Product product1 = createProduct(product1Id, "Product 1", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));
        Product product2  = createProduct(product2Id, "Product 2", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(200));
        when(productRepository.findAllByIdInWithLock(Set.of(product1Id, product2Id))).thenReturn(List.of(product1, product2));

        String errorMessage = "Database connection error during save";
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException(errorMessage));

        CurrentUser currentUser = new CurrentUser(customer.getId());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderServiceImpl.createOrder(request, currentUser)
        );
        assertEquals(errorMessage, exception.getMessage());

        verify(product1).decreaseStock(2);
        verify(product2).decreaseStock(3);

        verify(customerRepository).findById(customer.getId());
        verify(productRepository).findAllByIdInWithLock(Set.of(product1Id, product2Id));
        verify(orderRepository).save(any(Order.class));
        verifyNoInteractions(orderMapper);
    }

    private static AddressRequest createAddressRequest(String title, String city, String district, String zipCode, String country, String addressLine, String addressDetail) {
        return new AddressRequest(title, city, district, zipCode, country, addressLine, addressDetail);
    }

    private static Customer createCustomer() {
        Long customerId = 1L;
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "password");
        customer.setId(customerId);
        return customer;
    }

    private static Product createProduct(Long productId, String productName, ProductStatus productStatus, int stock, BigDecimal unitPrice) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn(productName);
        when(product.getStatus()).thenReturn(productStatus);
        when(product.getStock()).thenReturn(stock);
        when(product.getPrice()).thenReturn(unitPrice);

        return product;
    }

    private static CreateOrderRequest getCreateOrderRequest(Long product1Id, Long product2Id) {
        AddressRequest addressRequest = new AddressRequest(
                "Ev Adresi",
                "İstanbul",
                "Kadıköy",
                "34710",
                "Türkiye",
                "Caferağa Mah. Moda Cad. No:12",
                "Daire 4"
        );

        OrderItemRequest item1Request = new OrderItemRequest(product1Id, 2);
        OrderItemRequest item2Request = new OrderItemRequest(product2Id, 5);
        return new CreateOrderRequest(List.of(item1Request, item2Request), addressRequest, addressRequest);
    }
}
