package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
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

    private Order order;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "pass123");
        order = new Order(
                OrderStatus.PENDING,
                customer,
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                BigDecimal.valueOf(350.00)
        );
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

        when(customer.getId()).thenReturn(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 6, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 7, BigDecimal.valueOf(50));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(350));
        when(mockOrder.getId()).thenReturn(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"));
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
        when(customer.getId()).thenReturn(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 5, BigDecimal.valueOf(100));

        Order mockOrder = new Order(nonPendingStatus, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(200));
        when(mockOrder.getId()).thenReturn(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"));
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
        when(customer.getId()).thenReturn(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 15, BigDecimal.valueOf(50));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(400));
        when(mockOrder.getId()).thenReturn(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 4, new BigDecimal("50.00"));
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
        when(customer.getId()).thenReturn(customerId);

        Product missingProduct = createProduct(missingProductId, "Missing Product", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(100));

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(200));
        when(mockOrder.getId()).thenReturn(orderId);

        OrderItem item = new OrderItem(missingProductId, missingProduct.getName(), 2, new BigDecimal("100.00"));
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
        when(customer.getId()).thenReturn(customerId);

        Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 5, BigDecimal.valueOf(100));
        Product productB = createProduct(20L, "Product B", ProductStatus.ACTIVE, 10, BigDecimal.valueOf(50));

        doThrow(new RuntimeException("Stock calculation error or invariant violation"))
                .when(productB).increaseStock(3);

        Order mockOrder = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), BigDecimal.valueOf(350));
        when(mockOrder.getId()).thenReturn(orderId);

        OrderItem itemA = new OrderItem(productA.getId(), productA.getName(), 2, new BigDecimal("100.00"));
        OrderItem itemB = new OrderItem(productB.getId(), productB.getName(), 3, new BigDecimal("50.00"));
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
        OrderSummaryResponse returnedOrder = result.getContent().getFirst();
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
        verify(orderRepository).findOrderSummariesByCustomerId(eq(customerAId), any(Pageable.class));

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

    @Test
    @DisplayName("UpdateShippingAddress Unit Test 1 (Happy Path): PENDING durumundaki siparişin shipping adresi başarıyla değiştirilmeli")
    void updateShippingAddress_whenOrderIsPending_shouldUpdateAddressSuccessfully() {
        // GIVEN
        Customer customer = createCustomer();
        AddressRequest updateRequest = new AddressRequest(
                "Yeni Ev", "Ankara", "Çankaya", "06530", "Türkiye", "Kızılay Mah. No:10", "Daire 4"
        );

        Address initialShippingAddress = new Address("Eski Ev", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:5", "Daire 1");
        Address initialBillingAddress = new Address("Şirket", "İstanbul", "Şişli", "34360", "Türkiye", "Büyükdere Cad. No:100", "Kat 5");
        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("350.00"));
        when(order.getId()).thenReturn(101L);
        order.assignAddresses(initialShippingAddress, initialBillingAddress);

        CurrentUser currentUser = new CurrentUser(1L);

        when(orderRepository.findByIdAndCustomerId(101L, 1L))
                .thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(any(Order.class)))
                .thenReturn(mock(OrderResponse.class));

        // WHEN
        OrderResponse response = orderServiceImpl.updateShippingAddress(101L, updateRequest, currentUser);

        // THEN
        assertNotNull(response);
        assertEquals("Ankara", order.getShippingAddress().getCity());
        assertEquals("Çankaya", order.getShippingAddress().getDistrict());
        assertEquals("Yeni Ev", order.getShippingAddress().getTitle());

        verify(orderRepository).findByIdAndCustomerId(101L, 1L);
    }

    @Test
    @DisplayName("UpdateShippingAddress Unit Test 2 (IDOR Protection): Başka bir müşterinin sipariş ID'si gönderildiğinde OrderNotFoundException fırlatılmalı")
    void updateShippingAddress_whenOrderBelongsToOtherCustomer_shouldThrowOrderNotFoundException() {
        // GIVEN: Repository customerId=1L için sipariş bulamaz
        when(orderRepository.findByIdAndCustomerId(999L, 1L))
                .thenReturn(Optional.empty());

        AddressRequest updateRequest = new AddressRequest(
                "Yeni Ev", "Ankara", "Çankaya", "06530", "Türkiye", "Kızılay Mah. No:10", "Daire 4"
        );

        CurrentUser currentUser = new CurrentUser(1L);

        // WHEN & THEN
        assertThrows(
                ResourceNotFoundException.class,
                () -> orderServiceImpl.updateShippingAddress(999L, updateRequest, currentUser),
                "Başka müşterinin siparişi için ResourceNotFoundException fırlatılmalıdır."
        );

        verify(orderRepository).findByIdAndCustomerId(999L, 1L);
    }

    @Test
    @DisplayName("UpdateShippingAddress Unit Test 3 (CANCELLED Status): CANCELLED durumundaki sipariş güncellenmek istendiğinde OrderCannotBeUpdatedException fırlatılmalı")
    void updateShippingAddress_whenOrderIsCancelled_shouldThrowOrderCannotBeUpdatedException() {
        // GIVEN
        Customer customer = createCustomer();
        AddressRequest updateRequest = new AddressRequest(
                "Yeni Ev", "Ankara", "Çankaya", "06530", "Türkiye", "Kızılay Mah. No:10", "Daire 4"
        );

        Address initialShippingAddress = new Address("Eski Ev", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:5", "Daire 1");
        Address initialBillingAddress = new Address("Şirket", "İstanbul", "Şişli", "34360", "Türkiye", "Büyükdere Cad. No:100", "Kat 5");
        Order cancelledOrder = mock(Order.class);
        when(cancelledOrder.getId()).thenReturn(102L);
        when(cancelledOrder.getCustomer()).thenReturn(customer);
        when(cancelledOrder.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(cancelledOrder.getShippingAddress()).thenReturn(initialShippingAddress);
        when(cancelledOrder.getBillingAddress()).thenReturn(initialBillingAddress);

        CurrentUser currentUser = new CurrentUser(1L);

        when(orderRepository.findByIdAndCustomerId(102L, 1L))
                .thenReturn(Optional.of(cancelledOrder));

        // WHEN & THEN
        OrderCannotBeUpdatedException exception = assertThrows(
                OrderCannotBeUpdatedException.class,
                () -> orderServiceImpl.updateShippingAddress(102L, updateRequest, currentUser)
        );

        assertTrue(exception.getMessage().contains("CANCELLED"));
        assertEquals("İstanbul", cancelledOrder.getShippingAddress().getCity(), "Adres güncellenmemiş olmalıdır.");
    }

    @Test
    @DisplayName("UpdateShippingAddress Unit Test 4 (SHIPPED Status): SHIPPED durumundaki sipariş güncellenmek istendiğinde OrderCannotBeUpdatedException fırlatılmalı")
    void updateShippingAddress_whenOrderIsShipped_shouldThrowOrderCannotBeUpdatedException() {
        // GIVEN
        Customer customer = createCustomer();
        AddressRequest updateRequest = new AddressRequest(
                "Yeni Ev", "Ankara", "Çankaya", "06530", "Türkiye", "Kızılay Mah. No:10", "Daire 4"
        );

        Address initialShippingAddress = new Address("Eski Ev", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:5", "Daire 1");
        Address initialBillingAddress = new Address("Şirket", "İstanbul", "Şişli", "34360", "Türkiye", "Büyükdere Cad. No:100", "Kat 5");
        Order shippedOrder = mock(Order.class);
        when(shippedOrder.getId()).thenReturn(103L);
        when(shippedOrder.getCustomer()).thenReturn(customer);
        when(shippedOrder.getStatus()).thenReturn(OrderStatus.SHIPPED);
        when(shippedOrder.getShippingAddress()).thenReturn(initialShippingAddress);
        when(shippedOrder.getBillingAddress()).thenReturn(initialBillingAddress);

        CurrentUser currentUser = new CurrentUser(1L);

        when(orderRepository.findByIdAndCustomerId(103L, 1L))
                .thenReturn(Optional.of(shippedOrder));

        // WHEN & THEN
        OrderCannotBeUpdatedException exception = assertThrows(
                OrderCannotBeUpdatedException.class,
                () -> orderServiceImpl.updateShippingAddress(103L, updateRequest, currentUser)
        );

        assertTrue(exception.getMessage().contains("SHIPPED"));
        assertEquals("İstanbul", shippedOrder.getShippingAddress().getCity(), "Adres güncellenmemiş olmalıdır.");
    }

    @Test
    @DisplayName("Unit Test 5 (Billing Address Unchanged): Shipping adresi değişirken Billing adresi aynen kalmalı")
    void updateShippingAddress_shouldOnlyUpdateShippingAddressAndKeepBillingAddressUnchanged() {
        // GIVEN
        Customer customer = createCustomer();
        AddressRequest updateRequest = new AddressRequest(
                "Yeni Ev", "Ankara", "Çankaya", "06530", "Türkiye", "Kızılay Mah. No:10", "Daire 4"
        );

        Address initialShippingAddress = new Address("Eski Ev", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:5", "Daire 1");
        Address initialBillingAddress = new Address("Şirket", "İstanbul", "Şişli", "34360", "Türkiye", "Büyükdere Cad. No:100", "Kat 5");
        Order order = new Order(OrderStatus.PENDING, customer, customer.getPhone(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), new BigDecimal("350.00"));
        when(order.getId()).thenReturn(104L);
        order.assignAddresses(initialShippingAddress, initialBillingAddress);

        CurrentUser currentUser = new CurrentUser(1L);

        when(orderRepository.findByIdAndCustomerId(104L, 1L))
                .thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(any(Order.class)))
                .thenReturn(mock(OrderResponse.class));

        // WHEN
        orderServiceImpl.updateShippingAddress(104L, updateRequest, currentUser);

        // THEN: Shipping güncellendi
        assertEquals("Ankara", order.getShippingAddress().getCity());
        assertEquals("Çankaya", order.getShippingAddress().getDistrict());

        // THEN: Billing kesinlikle değişmedi (Snapshot Korundu)
        assertEquals("Şirket", order.getBillingAddress().getTitle());
        assertEquals("İstanbul", order.getBillingAddress().getCity());
        assertEquals("Şişli", order.getBillingAddress().getDistrict());
        assertEquals("Büyükdere Cad. No:100", order.getBillingAddress().getAddressLine());
    }

    @Nested
    @DisplayName("startPreparing testleri")
    class StartPreparingTests {
        @Test
        @DisplayName("startPreparing: Sipariş PAID durumundayken başarılı bir şekilde PREPARING durumuna geçmeli ve güncel OrderResponse dönmeli")
        void startPreparing_whenOrderExistsAndIsPaid_shouldStartPreparingAndReturnResponse() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);

            Product productA = createProduct(10L, "Product A", ProductStatus.ACTIVE, 6, BigDecimal.valueOf(100));

            OrderItemResponse itemResponseA = new OrderItemResponse(11L, productA.getId(), productA.getName(), 2, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.valueOf(200));
            AddressResponse addressResponse = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2");

            OrderResponse expectedResponse = new OrderResponse(
                    orderId,
                    createdAt,
                    OrderStatus.PREPARING,
                    customer.getId(),
                    new BigDecimal("350.00"),
                    List.of(itemResponseA),
                    addressResponse,
                    addressResponse
            );

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderMapper.toOrderResponse(order)).thenReturn(expectedResponse);

            // Act
            OrderResponse result = orderServiceImpl.startPreparing(orderId);

            // Assert
            assertNotNull(result);
            assertEquals(OrderStatus.PREPARING, result.status());
            assertEquals(OrderStatus.PREPARING, order.getStatus());
            verify(orderRepository).findById(orderId);
            verify(orderMapper).toOrderResponse(order);
        }

        @Test
        @DisplayName("startPreparing: Sipariş veritabanında bulunamadığında ResourceNotFoundException fırlatmalı")
        void startPreparing_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            Long orderId = 999L;
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> orderServiceImpl.startPreparing(orderId));
            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }

        @Test
        @DisplayName("startPreparing: Sipariş geçersiz bir durumda (örn. SHIPPED) olduğunda domain exception'ı yutmadan (swallow etmeden) fırlatmalı")
        void startPreparing_whenOrderStatusIsInvalid_shouldPropagateOrderStatusTransitionException() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> orderServiceImpl.startPreparing(orderId)
            );

            assertEquals(OrderStatus.SHIPPED, order.getStatus(), "Domain seviyesinde exception fırlatıldığı için statü SHIPPED olarak kalmalı");
            assertTrue(exception.getMessage().contains(OrderStatus.SHIPPED.name()));
            assertTrue(exception.getMessage().contains(OrderStatus.PREPARING.name()));

            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    @DisplayName("markAsShipped testleri")
    class markAsShippedTests {
        @Test
        @DisplayName("markAsShipped: Sipariş PREPARING durumundayken başarılı bir şekilde SHIPPED olmalı, shippedAt set edilmeli ve OrderResponse dönmeli")
        void markAsShipped_whenOrderExistsAndIsPreparing_shouldMarkAsShippedAndReturnResponse() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.PREPARING);

            OrderItemResponse itemResponseA = new OrderItemResponse(11L, 10L, "Test Product", 2, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.valueOf(200));
            AddressResponse addressResponse = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2");

            OrderResponse expectedResponse = new OrderResponse(
                    orderId,
                    createdAt,
                    OrderStatus.SHIPPED,
                    customer.getId(),
                    new BigDecimal("350.00"),
                    List.of(itemResponseA),
                    addressResponse,
                    addressResponse
            );

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderMapper.toOrderResponse(order)).thenReturn(expectedResponse);

            // Act
            OrderResponse result = orderServiceImpl.markAsShipped(orderId);

            // Assert
            assertNotNull(result);
            assertEquals(OrderStatus.SHIPPED, order.getStatus());
            assertEquals(OrderStatus.SHIPPED, result.status());
            assertNotNull(order.getShippedAt(), "shippedAt alanı doldurulmuş olmalı");
            verify(orderRepository).findById(orderId);
            verify(orderMapper).toOrderResponse(order);
        }

        @Test
        @DisplayName("markAsShipped: Sipariş veritabanında bulunamadığında ResourceNotFoundException fırlatmalı")
        void markAsShipped_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            Long orderId = 999L;
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> orderServiceImpl.markAsShipped(orderId));
            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }

        @Test
        @DisplayName("markAsShipped: PREPARING dışındaki bir durumda (örn. PENDING) OrderStatusTransitionException dışarı fırlatılmalı")
        void markAsShipped_whenOrderStatusIsInvalid_shouldPropagateOrderStatusTransitionException() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.PENDING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> orderServiceImpl.markAsShipped(orderId)
            );

            assertEquals(OrderStatus.PENDING, order.getStatus(), "Domain exception fırlatıldığı için statü korunuş olmalı");
            assertNull(order.getShippedAt(), "shippedAt set edilmemiş olmalı");
            assertTrue(exception.getMessage().contains(OrderStatus.PENDING.name()));
            assertTrue(exception.getMessage().contains(OrderStatus.SHIPPED.name()));

            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }
    }

    @Nested
    @DisplayName("markAsDelivered testleri")
    class markAsDeliveredTests {
        @Test
        @DisplayName("markAsDelivered: Sipariş SHIPPED durumundayken başarılı şekilde DELIVERED olmalı, deliveredAt set edilmeli ve OrderResponse dönmeli")
        void markAsDelivered_whenOrderExistsAndIsShipped_shouldMarkAsDeliveredAndReturnResponse() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);

            OrderItemResponse itemResponseA = new OrderItemResponse(11L, 10L, "Test Product", 2, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.valueOf(200));
            AddressResponse addressResponse = new AddressResponse("Ev Adresi", "İstanbul", "Kadıköy", "34710", "Türkiye", "Moda Cad. No:1", "D 2");

            OrderResponse expectedResponse = new OrderResponse(
                    orderId,
                    createdAt,
                    OrderStatus.DELIVERED,
                    customer.getId(),
                    new BigDecimal("350.00"),
                    List.of(itemResponseA),
                    addressResponse,
                    addressResponse
            );
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderMapper.toOrderResponse(order)).thenReturn(expectedResponse);

            // Act
            OrderResponse result = orderServiceImpl.markAsDelivered(orderId);

            // Assert
            assertNotNull(result);
            assertEquals(OrderStatus.DELIVERED, order.getStatus());
            assertEquals(OrderStatus.DELIVERED, result.status());
            assertNotNull(order.getDeliveredAt(), "deliveredAt alanı doldurulmuş olmalı");
            verify(orderRepository).findById(orderId);
            verify(orderMapper).toOrderResponse(order);
        }

        @Test
        @DisplayName("markAsDelivered: Sipariş veritabanında bulunamadığında ResourceNotFoundException fırlatmalı")
        void markAsDelivered_whenOrderNotFound_shouldThrowResourceNotFoundException() {
            // Arrange
            Long orderId = 999L;
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> orderServiceImpl.markAsDelivered(orderId));
            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }

        @Test
        @DisplayName("markAsDelivered: SHIPPED dışındaki bir durumda (örn. PREPARING) OrderStatusTransitionException dışarı fırlatılmalı")
        void markAsDelivered_whenOrderStatusIsInvalid_shouldPropagateOrderStatusTransitionException() throws Exception {
            // Arrange
            Long orderId = 100L;
            ReflectionTestUtils.setField(order, "status", OrderStatus.PREPARING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> orderServiceImpl.markAsDelivered(orderId)
            );

            assertEquals(OrderStatus.PREPARING, order.getStatus(), "Domain exception fırlatıldığı için statü korunmalı");
            assertNull(order.getDeliveredAt(), "deliveredAt set edilmemiş olmalı");
            assertTrue(exception.getMessage().contains(OrderStatus.PREPARING.name()));
            assertTrue(exception.getMessage().contains(OrderStatus.DELIVERED.name()));

            verify(orderRepository).findById(orderId);
            verifyNoInteractions(orderMapper);
        }
    }

    private static AddressRequest createAddressRequest(String title, String city, String district, String zipCode, String country, String addressLine, String addressDetail) {
        return new AddressRequest(title, city, district, zipCode, country, addressLine, addressDetail);
    }

    private static Customer createCustomer() {
        Long customerId = 1L;
        Customer customer = new Customer("Caner", "Demir", "caner@example.com", "5551234567", "password");
        when(customer.getId()).thenReturn(customerId);
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
}
