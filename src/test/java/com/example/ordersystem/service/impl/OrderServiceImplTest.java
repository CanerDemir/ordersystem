package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.request.OrderItemRequest;
import com.example.ordersystem.dto.response.AddressResponse;
import com.example.ordersystem.dto.response.OrderItemResponse;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.dto.response.OrderSummaryResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.*;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

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

    @Captor
    private ArgumentCaptor<Set<Long>> productIdsCaptor;

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

    @Test
    @DisplayName("Get Order Unit Test 1 (Happy Path): Sipariş müşteriyle eşleştiğinde OrderResponse ve mapper doğru parametrelerle dönmeli")
    void getOrderDetail_whenOrderExistsAndBelongsToCustomer_shouldReturnOrderDetailResponse() {
        Long orderId  = 100L;

        Customer customer = createCustomer();

        Product product = createProduct(10L, "Product 1", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(225));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(450), createdAt);
        mockOrder.setId(orderId);

        OrderItem orderItem = new OrderItem(product.getId(), product.getName(), 2, new BigDecimal("225.00"), BigDecimal.valueOf(450));
        when(mockOrder.getItems()).thenReturn(List.of(orderItem));

        AddressResponse shippingAddrResp = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2");
        AddressResponse billingAddrResp = new AddressResponse("Fatura Adresi", "Ankara", "Çankaya", "06540", "Türkiye", "Atatürk Bulvarı No:100", "Kat 2");
        OrderItemResponse itemResponse = new OrderItemResponse(11L, product.getId(), product.getName(), 2, new BigDecimal("225.00"), BigDecimal.ZERO, BigDecimal.valueOf(450));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                createdAt,
                OrderStatus.PENDING,
                customer.getId(),
                new BigDecimal("450.00"),
                List.of(itemResponse),
                shippingAddrResp,
                billingAddrResp
        );

        when(orderRepository.findByIdAndCustomerId(orderId, customer.getId())).thenReturn(Optional.of(mockOrder));
        when(orderMapper.toOrderResponse(mockOrder)).thenReturn(expectedResponse);

        CurrentUser currentUser = new CurrentUser(customer.getId());
        OrderResponse actualReponse = orderServiceImpl.getOrderById(orderId, currentUser);
        assertNotNull(actualReponse);
        assertEquals(orderId, actualReponse.id());
        assertEquals(customer.getId(), actualReponse.customerId());
        assertEquals(OrderStatus.PENDING, actualReponse.status());
        assertEquals(0, new BigDecimal("450.00").compareTo(actualReponse.totalAmount()));
        assertEquals(1, actualReponse.items().size());

        verify(orderRepository).findByIdAndCustomerId(orderId, customer.getId());
        verify(orderMapper).toOrderResponse(mockOrder);
    }

    @Test
    @DisplayName("Get Order Unit Test 2: Sipariş bulunamadığında veya başka müşteriye ait olduğunda ResourceNotFoundException fırlatılmalı ve Mapper çalışmamalı")
    void getOrderDetail_whenOrderDoesNotExist_shouldThrowResourceNotFoundException() {
        Long orderId  = 999L;
        Long customerId = 1L;

        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        CurrentUser currentUser = new CurrentUser(customerId);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.getOrderById(orderId, currentUser),
                "Sipariş bulunamadığında ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(String.valueOf(orderId)));

        verify(orderRepository).findByIdAndCustomerId(orderId, customerId);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Get Order Unit Test 3 (IDOR Protection): Müşteri başkasına ait siparişi sorguladığında DB Optional.empty döner ve aynı ResourceNotFoundException fırlatılır")
    void getOrderDetail_whenOrderBelongsToAnotherCustomer_shouldReturnEmptyAndThrowResourceNotFoundException() {
        Long targetOrderId = 100L;
        Long attackerCustomerId = 99L;

        when(orderRepository.findByIdAndCustomerId(targetOrderId, attackerCustomerId))
                .thenReturn(Optional.empty());

        CurrentUser currentUser = new CurrentUser(attackerCustomerId);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.getOrderById(targetOrderId, currentUser),
                "Başka müşterinin siparişi sorgulandığında da ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(String.valueOf(targetOrderId)));

        verify(orderRepository).findByIdAndCustomerId(targetOrderId, attackerCustomerId);
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Cancel Order Unit Test 1 (Happy Path): PENDING sipariş iptal edildiğinde stoklar iade edilmeli ve status CANCELLED olmalı")
    void cancelOrder_whenOrderIsPendingAndBelongsToCustomer_shouldCancelOrderAndRestoreStock() {
        Long orderId = 100L;
        Long customerId = 1L;
        CurrentUser currentUser = new CurrentUser(customerId);

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer.setId(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 6, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 7, BigDecimal.valueOf(50));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(350), createdAt);
        mockOrder.setId(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"),  BigDecimal.valueOf(150));
        when(mockOrder.getItems()).thenReturn(List.of(itemA, itemB));

        OrderItemResponse itemResponseA = new OrderItemResponse(11L, productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.valueOf(200));
        OrderItemResponse itemResponseB = new OrderItemResponse(12L, productB.getId(), productB.getName(), 3, new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.valueOf(150));
        AddressResponse addressResponse = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2");

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                createdAt,
                OrderStatus.CANCELLED,
                customerId,
                new BigDecimal("350.00"),
                List.of(itemResponseA, itemResponseB),
                addressResponse,
                addressResponse
        );

        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId))
                .thenReturn(Optional.of(mockOrder));
        when(productRepository.findAllByIdInWithLock(Set.of(productA.getId(), productB.getId())))
                .thenReturn(List.of(productA, productB));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toOrderResponse(mockOrder))
                .thenReturn(expectedResponse);

        OrderResponse actualResponse = orderServiceImpl.cancelOrder(orderId, currentUser);

        assertNotNull(actualResponse);
        assertEquals(OrderStatus.CANCELLED, actualResponse.status());
        assertEquals(OrderStatus.CANCELLED, mockOrder.getStatus());

        assertEquals(8, productA.getStock(), "Product A stoğu 6 + 2 = 8 olmalıdır.");
        assertEquals(10, productB.getStock(), "Product B stoğu 7 + 3 = 10 olmalıdır.");
        assertEquals(expectedResponse, actualResponse);

        verify(orderRepository).findByIdAndCustomerIdWithLock(orderId, customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(productA.getId(), productB.getId()));
        verify(orderRepository).save(mockOrder);
        verify(orderMapper).toOrderResponse(mockOrder);
    }

    @Test
    @DisplayName("Cancel Order Unit Test 2 (Cancel IDOR Protection): Başka müşterinin siparişi iptal edilmeye çalışıldığında ResourceNotFoundException fırlatılmalı ve hiçbir veritabanı/yazma işlemi gerçekleşmemeli")
    void cancelOrder_whenOrderDoesNotExistOrBelongsToAnotherCustomer_shouldThrowResourceNotFoundException() {
        Long targetOrderId = 100L;
        Long attackerCustomerId = 99L;
        CurrentUser currentUser = new CurrentUser(attackerCustomerId);

        when(orderRepository.findByIdAndCustomerIdWithLock(targetOrderId, attackerCustomerId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.cancelOrder(targetOrderId, currentUser),
                "Başka bir müşterinin siparişi iptal edilmeye çalışıldığında ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(targetOrderId.toString()));

        verify(orderRepository).findByIdAndCustomerIdWithLock(targetOrderId, attackerCustomerId);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(orderMapper);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Cancel Order Unit Test 3: Sipariş durumu PENDING dışında bir değer olduğunda OrderCannotBeCancelledException fırlatılmalı, kilit alınmamalı ve stok değişmemeli")
    void cancelOrder_whenOrderStatusIsNotPending_shouldThrowOrderCannotBeCancelledException(OrderStatus nonPendingStatus) {
        Long orderId = 100L;
        Long customerId = 1L;
        CurrentUser currentUser = new CurrentUser(customerId);

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer.setId(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 5, BigDecimal.valueOf(100));

        Order mockOrder = new Order(nonPendingStatus, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(200), createdAt);
        mockOrder.setId(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        when(mockOrder.getItems()).thenReturn(List.of(itemA));

        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId))
                .thenReturn(Optional.of(mockOrder));

        OrderCannotBeCancelledException exception = assertThrows(
                OrderCannotBeCancelledException.class,
                () -> orderServiceImpl.cancelOrder(orderId, currentUser),
                "PENDING dışındaki siparişler için OrderCannotBeCancelledException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(orderId.toString()));

        assertEquals(5, productA.getStock(), "İptal başarısız olduğu için stok miktarı değişmemelidir.");

        verify(orderRepository).findByIdAndCustomerIdWithLock(orderId, customerId);
        verifyNoInteractions(productRepository);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Cancel Order Unit Test 4: Siparişteki tüm ürün ID'leri Set olarak toplanıp lock repository'sine gönderilmeli ve her ürünün stoğu doğru miktarda (2 ve 4) artırılmalı")
    void cancelOrder_shouldCollectProductIdsAsSetAndIncreaseStockForEveryProduct() {
        // GIVEN
        Long orderId = 100L;
        Long customerId = 1L;
        CurrentUser  currentUser = new CurrentUser(customerId);

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer.setId(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 15, BigDecimal.valueOf(50));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(400), createdAt);
        mockOrder.setId(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 4, new BigDecimal("50.00"), BigDecimal.valueOf(200));
        when(mockOrder.getItems()).thenReturn(List.of(itemA, itemB));

        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId))
                .thenReturn(Optional.of(mockOrder));
        when(productRepository.findAllByIdInWithLock(any()))
                .thenReturn(List.of(productA, productB));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderServiceImpl.cancelOrder(orderId, currentUser);

        verify(productRepository).findAllByIdInWithLock(productIdsCaptor.capture());
        Set<Long> capturedProductIds = productIdsCaptor.getValue();

        assertEquals(2, capturedProductIds.size(), "Lock repository'sine gönderilen ID kümesi 2 elemanlı olmalıdır.");
        assertTrue(capturedProductIds.contains(10L), "ID kümesi Product A'nın ID'sini (10) içermelidir.");
        assertTrue(capturedProductIds.contains(20L), "ID kümesi Product B'nin ID'sini (20) içermelidir.");

        verify(productA).increaseStock(2);
        verify(productB).increaseStock(4);

        assertEquals(12, productA.getStock(), "Product A stoğu (10 + 2 = 12) olmalıdır.");
        assertEquals(19, productB.getStock(), "Product B stoğu (15 + 4 = 19) olmalıdır.");
    }

    @Test
    @DisplayName("Cancel Order Unit Test 5: Lock sorgusu sonucunda sipariş kalemi olan bir ürün DB'de bulunamazsa ResourceNotFoundException fırlatılmalı ve save yapılmamalı")
    void cancelOrder_whenProductNotFoundInLockQuery_shouldThrowResourceNotFoundException() {
        Long orderId = 100L;
        Long customerId = 1L;
        Long missingProductId = 99L;
        CurrentUser  currentUser = new CurrentUser(customerId);

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer.setId(customerId);

        Product missingProduct = createProduct(missingProductId, "Missing Product", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(200), createdAt);
        mockOrder.setId(orderId);

        OrderItem item = new OrderItem(missingProductId, missingProduct.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        when(mockOrder.getItems()).thenReturn(List.of(item));

        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId))
                .thenReturn(Optional.of(mockOrder));
        when(productRepository.findAllByIdInWithLock(Set.of(missingProductId)))
                .thenReturn(Collections.emptyList());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.cancelOrder(orderId, currentUser),
                "Lock sorgusunda ürün bulunamadığında ResourceNotFoundException fırlatılmalıdır."
        );

        assertTrue(exception.getMessage().contains(missingProductId.toString()));

        verify(orderRepository).findByIdAndCustomerIdWithLock(orderId, customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(missingProductId));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("Cancel Order Unit Test 6: Ürün stoğu artırılırken RuntimeException fırlatılırsa hata yukarı fırlatılmalı, save ve mapper çağrılmamalı")
    void cancelOrder_whenIncreaseStockThrowsException_shouldPropagateExceptionAndNotSaveOrder() {
        Long orderId = 100L;
        Long customerId = 1L;
        CurrentUser  currentUser = new CurrentUser(customerId);

        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        customer.setId(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 5, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(50));

        doThrow(new RuntimeException("Stock calculation error or invariant violation"))
                .when(productB).increaseStock(3);

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(350), createdAt);
        mockOrder.setId(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.valueOf(200));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"),  BigDecimal.valueOf(150));
        when(mockOrder.getItems()).thenReturn(List.of(itemA, itemB));

        when(orderRepository.findByIdAndCustomerIdWithLock(orderId, customerId))
                .thenReturn(Optional.of(mockOrder));
        when(productRepository.findAllByIdInWithLock(Set.of(10L, 20L)))
                .thenReturn(List.of(productA, productB));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderServiceImpl.cancelOrder(orderId, currentUser),
                "Ürün stok güncelemesinde fırlatılan RuntimeException yukarı iletilmelidir."
        );

        assertEquals("Stock calculation error or invariant violation", exception.getMessage());

        verify(orderRepository).findByIdAndCustomerIdWithLock(orderId, customerId);
        verify(productRepository).findAllByIdInWithLock(Set.of(10L, 20L));

        verify(productA).increaseStock(2);
        verify(productB).increaseStock(3);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 1 (Happy Path): 3 sipariş arasından page=0, size=2 istendiğinde en yeni 2 sipariş (Order 3 ve Order 2) dönmeli, totalElements=3, totalPages=2 olmalıdır")
    void getCustomerOrders_happyPath_shouldReturnPaginatedAndSortedOrders() {
        Instant date1 = Instant.parse("2026-08-28T10:00:00Z");
        Instant date2 = Instant.parse("2026-08-29T10:00:00Z");
        Instant date3 = Instant.parse("2026-08-30T10:00:00Z");

        OrderSummaryResponse order1 = new OrderSummaryResponse(1L, date1, OrderStatus.PENDING, new BigDecimal("100.00"), 2);
        OrderSummaryResponse order2 = new OrderSummaryResponse(2L, date2, OrderStatus.DELIVERED, new BigDecimal("200.00"), 3);
        OrderSummaryResponse order3 = new OrderSummaryResponse(3L, date3, OrderStatus.CANCELLED, new BigDecimal("300.00"), 1);

        Pageable requestPageable = PageRequest.of(0, 2);

        List<OrderSummaryResponse> pageContent = List.of(order3, order2);
        Page<OrderSummaryResponse> mockPage = new PageImpl<>(pageContent, requestPageable, 3);

        when(orderRepository.findOrderSummariesByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(mockPage);

        CurrentUser currentUser = new CurrentUser(1L);

        Page<OrderSummaryResponse> result = orderServiceImpl.getCustomerOrders(currentUser, requestPageable);

        assertNotNull(result);
        assertEquals(3, result.getTotalElements(), "Toplam eleman sayısı 3 olmalıdır.");
        assertEquals(2, result.getTotalPages(), "Toplam sayfa sayısı 2 olmalıdır.");
        assertEquals(2, result.getContent().size(), "Sayfadaki eleman sayısı 2 olmalıdır.");

        assertEquals(3L, result.getContent().get(0).id(), "İlk eleman Order 3 (2026-08-30) olmalıdır.");
        assertEquals(2L, result.getContent().get(1).id(), "İkinci eleman Order 2 (2026-08-29) olmalıdır.");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findOrderSummariesByCustomerId(eq(1L), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(0, capturedPageable.getPageNumber());
        assertEquals(2, capturedPageable.getPageSize());

        Sort sort = capturedPageable.getSort();
        assertEquals(Sort.Order.desc("createdAt"), sort.getOrderFor("createdAt"));
        assertEquals(Sort.Order.desc("id"), sort.getOrderFor("id"));
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 2 (Second Page): page=1, size=2 istendiğinde yalnızca 2. sayfada kalan Order 1 dönmeli, totalElements=3, totalPages=2 ve content.size()=1 olmalıdır")
    void getCustomerOrders_secondPage_shouldReturnRemainingOrder() {
        // GIVEN: UTC Instant Zaman Damgaları
        Instant date1 = Instant.parse("2026-08-28T10:00:00Z");
        Instant date2 = Instant.parse("2026-08-29T10:00:00Z");
        Instant date3 = Instant.parse("2026-08-30T10:00:00Z");

        OrderSummaryResponse order1 = new OrderSummaryResponse(1L, date1, OrderStatus.PENDING, new BigDecimal("100.00"), 2);

        // Request: 2. sayfa (pageIndex = 1)
        Pageable requestPageable = PageRequest.of(1, 2);

        // Repository Mock: 2. Sayfada yalnızca Order 1 var, toplam kayıt sayısı 3
        List<OrderSummaryResponse> secondPageContent = List.of(order1);
        Page<OrderSummaryResponse> mockPage = new PageImpl<>(secondPageContent, requestPageable, 3);

        when(orderRepository.findOrderSummariesByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(mockPage);

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN
        Page<OrderSummaryResponse> result = orderServiceImpl.getCustomerOrders(currentUser, requestPageable);

        // THEN
        assertNotNull(result);
        assertEquals(3, result.getTotalElements(), "Toplam eleman sayısı 3 olmalıdır.");
        assertEquals(2, result.getTotalPages(), "Toplam sayfa sayısı 2 olmalıdır.");
        assertEquals(1, result.getContent().size(), "İkinci sayfada yalnızca 1 eleman (Order 1) bulunmalıdır.");

        // İçerik Kontrolü
        OrderSummaryResponse returnedOrder = result.getContent().get(0);
        assertEquals(1L, returnedOrder.id(), "İkinci sayfadaki eleman Order 1 olmalıdır.");
        assertEquals(date1, returnedOrder.createdAt(), "Tarih 2026-08-28T10:00:00Z olmalıdır.");

        // Repository Parametre Doğrulaması
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findOrderSummariesByCustomerId(eq(1L), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(1, capturedPageable.getPageNumber(), "Sorgulanan sayfa indeksi 1 olmalıdır.");
        assertEquals(2, capturedPageable.getPageSize(), "Sayfa boyutu 2 olmalıdır.");
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 3 (Customer Isolation): Customer A (3 sipariş) talepte bulunduğunda yalnızca kendisine ait siparişler dönmeli, Customer B'ye (5 sipariş) ait veriler sızmamalıdır")
    void getCustomerOrders_customerIsolation_shouldOnlyReturnAuthenticatedCustomerOrders() {
        // GIVEN
        Long customerAId = 1L;
        Long customerBId = 2L;

        Instant now = Instant.now();

        // Customer A'ya ait 3 sipariş
        OrderSummaryResponse orderA1 = new OrderSummaryResponse(101L, now.minusSeconds(3600), OrderStatus.DELIVERED, new BigDecimal("150.00"), 2);
        OrderSummaryResponse orderA2 = new OrderSummaryResponse(102L, now.minusSeconds(1800), OrderStatus.DELIVERED, new BigDecimal("250.00"), 1);
        OrderSummaryResponse orderA3 = new OrderSummaryResponse(103L, now, OrderStatus.PENDING, new BigDecimal("350.00"), 4);

        Pageable requestPageable = PageRequest.of(0, 10);

        // Customer A için yalnızca A'nın 3 siparişini içeren mock yanıt
        List<OrderSummaryResponse> customerAOrders = List.of(orderA3, orderA2, orderA1);
        Page<OrderSummaryResponse> mockPageForCustomerA = new PageImpl<>(customerAOrders, requestPageable, 3);

        // Mocking: Repository çağrısı Customer A ID'si (1L) ile yapıldığında A'nın verileri döner
        when(orderRepository.findOrderSummariesByCustomerId(eq(customerAId), any(Pageable.class)))
                .thenReturn(mockPageForCustomerA);

        CurrentUser currentUserA = new CurrentUser(customerAId);

        // WHEN: Customer A (id=1L) yetkilendirme bilgisiyle servis çağrısı yapılır
        Page<OrderSummaryResponse> result = orderServiceImpl.getCustomerOrders(currentUserA, requestPageable);

        // THEN
        assertNotNull(result);
        assertEquals(3, result.getTotalElements(), "Customer A için totalElements tam olarak 3 olmalıdır.");
        assertEquals(3, result.getContent().size(), "Dönen listedeki eleman sayısı 3 olmalıdır.");

        // Dönen tüm siparişlerin Customer A'ya ait olduğunun doğrulanması
        List<Long> returnedOrderIds = result.getContent().stream()
                .map(OrderSummaryResponse::id)
                .toList();

        assertTrue(returnedOrderIds.containsAll(List.of(101L, 102L, 103L)), "Sonuç kümesi yalnızca Customer A'nın sipariş ID'lerini içermelidir.");

        // ISOLATION VERIFICATION:
        // 1. Repository'ye kesinlikle Customer A ID'sinin (1L) iletildiği doğrulanır
        verify(orderRepository, times(1)).findOrderSummariesByCustomerId(eq(customerAId), any(Pageable.class));

        // 2. Repository'nin Customer B ID'si (2L) ile HiÇ ÇAĞRILMADIĞI doğrulanır (Sızıntı engeli)
        verify(orderRepository, never()).findOrderSummariesByCustomerId(eq(customerBId), any(Pageable.class));
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 4 (No Orders): Hiç siparişi olmayan bir müşteri sorguladığında content=[], totalElements=0 ve totalPages=0 dönmelidir")
    void getCustomerOrders_whenNoOrders_shouldReturnEmptyPage() {
        // GIVEN
        Pageable requestPageable = PageRequest.of(0, 20);

        // Repository hiç sipariş bulamadığında empty Page döner
        when(orderRepository.findOrderSummariesByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty(requestPageable));

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN
        Page<OrderSummaryResponse> result = orderServiceImpl.getCustomerOrders(currentUser, requestPageable);

        // THEN
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty(), "Siparişi olmayan müşteri için liste boş dönmelidir.");
        assertEquals(0, result.getTotalElements(), "totalElements 0 olmalıdır.");
        assertEquals(0, result.getTotalPages(), "totalPages 0 olmalıdır.");
        assertEquals(0, result.getNumberOfElements(), "Sayfadaki eleman sayısı 0 olmalıdır.");

        // Repository parametre doğrulaması
        verify(orderRepository).findOrderSummariesByCustomerId(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 5 (Stable Ordering): Birebir aynı createdAt zaman damgasına sahip 2 sipariş olduğunda ikincil id DESC kriteri devreye girmeli ve id'si büyük olan (Order B - id:200) ilk sırada dönmelidir")
    void getCustomerOrders_sameCreatedAt_shouldApplySecondaryIdSortDeterministically() {
        // GIVEN: İki sipariş için birebir aynı Instant zaman damgası (Milisaniye seviyesinde eşit)
        Instant exactSameTimestamp = Instant.parse("2026-09-01T15:30:00.000Z");

        // Order A: id = 100L
        OrderSummaryResponse orderA = new OrderSummaryResponse(
                100L, exactSameTimestamp, OrderStatus.PENDING, new BigDecimal("150.00"), 2
        );

        // Order B: id = 200L (Aynı tarihte ancak ID'si daha büyük)
        OrderSummaryResponse orderB = new OrderSummaryResponse(
                200L, exactSameTimestamp, OrderStatus.SHIPPED, new BigDecimal("300.00"), 5
        );

        Pageable requestPageable = PageRequest.of(0, 10);

        // Repository ikincil "id DESC" sıralamasını uyguladığı için id'si büyük olan Order B önde gelir
        List<OrderSummaryResponse> deterministicContent = List.of(orderB, orderA);
        Page<OrderSummaryResponse> mockPage = new PageImpl<>(deterministicContent, requestPageable, 2);

        when(orderRepository.findOrderSummariesByCustomerId(eq(1L), any(Pageable.class)))
                .thenReturn(mockPage);

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN
        Page<OrderSummaryResponse> result = orderServiceImpl.getCustomerOrders(currentUser, requestPageable);

        // THEN
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        // Deterministic Sıralama Kontrolü: Tarihler eşit olduğundan ID'si büyük olan Order B (200L) ilk sırada gelmelidir
        assertEquals(200L, result.getContent().get(0).id(), "Tarihler eşit olduğundan ikincil id DESC sıralamasıyla ID=200 ilk sırada yer almalıdır.");
        assertEquals(100L, result.getContent().get(1).id(), "ID=100 olan ikinci sırada yer almalıdır.");

        // Servis katmanının repository'ye ilettiği Sort kurallarının doğrulanması
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findOrderSummariesByCustomerId(eq(1L), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        Sort sort = capturedPageable.getSort();

        // Birincil ve ikincil sıralama kurallarının eksiksiz varlığı doğrulanır
        assertNotNull(sort.getOrderFor("createdAt"), "createdAt sıralaması tanımlı olmalıdır.");
        assertNotNull(sort.getOrderFor("id"), "Determinism için ikincil id sıralaması tanımlı olmalıdır.");
        assertEquals(Sort.Direction.DESC, Objects.requireNonNull(sort.getOrderFor("createdAt")).getDirection());
        assertEquals(Sort.Direction.DESC, Objects.requireNonNull(sort.getOrderFor("id")).getDirection());
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 6 (Page Size Limit): İstemci size=101 (MAX_PAGE_SIZE=100 sınırından büyük) gönderdiğinde InvalidPageSizeException fırlatılmalı ve DB sorgusu çağrılmamalıdır")
    void getCustomerOrders_sizeExceedsLimit_shouldThrowInvalidPageSizeException() {
        // GIVEN
        Pageable requestPageable = PageRequest.of(0, 101); // Kısıt aşımı (101 > 100)

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN & THEN
        InvalidPageSizeException exception = assertThrows(
                InvalidPageSizeException.class,
                () -> orderServiceImpl.getCustomerOrders(currentUser, requestPageable),
                "size > 100 olduğunda InvalidPageSizeException fırlatılmalıdır."
        );

        // Hata mesajı doğrulaması
        assertEquals("Page size 101 exceeds maximum allowed limit of 100", exception.getMessage());

        // Validation aşamasında patladığı için repository'nin HİÇ çağrılmadığının doğrulanması
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("GetMyOrders Unit Test List 7 (Invalid Page): İstemci page=-1 gönderdiğinde InvalidPageIndexException fırlatılmalı ve DB sorgusu çağrılmamalıdır")
    void getCustomerOrders_negativePageIndex_shouldThrowInvalidPageIndexException() {
        // GIVEN: Negatif sayfa indeksi (page = -1)
        // PageRequest.of(-1, 20) doğrudan IllegalArgumentException fırlatacağı için
        // Mockito veya Custom Pageable interface mock'u ile test edilir
        Pageable mockInvalidPageable = mock(Pageable.class);
        when(mockInvalidPageable.getPageNumber()).thenReturn(-1);
        when(mockInvalidPageable.getPageSize()).thenReturn(20);

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN & THEN
        InvalidPageIndexException exception = assertThrows(
                InvalidPageIndexException.class,
                () -> orderServiceImpl.getCustomerOrders(currentUser, mockInvalidPageable),
                "page < 0 olduğunda InvalidPageIndexException fırlatılmalıdır."
        );

        // Hata mesajı doğrulaması
        assertEquals("Page index must not be less than zero. Requested page: -1", exception.getMessage());

        // Validation aşamasında patladığı için repository'nin HİÇ çağrılmadığının doğrulanması
        verifyNoInteractions(orderRepository);
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
