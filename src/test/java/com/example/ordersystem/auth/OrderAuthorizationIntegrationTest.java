package com.example.ordersystem.auth;

import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.request.OrderItemRequest;
import com.example.ordersystem.entity.Address;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    private Customer customerA;
    private Customer customerB;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        customerA = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "password1"));
        customerB = customerRepository.save(new Customer("Ahmet", "Yılmaz", "ahmet@example.com", "+905554445566", "password2"));

        testProduct = new Product("Test Laptop", new BigDecimal("1000.00"), 10, "Test product", ProductStatus.ACTIVE, Instant.now(), Instant.now());
        testProduct = productRepository.save(testProduct);
    }

    /**
     * CurrentUser ve GrantedAuthority taşıyan Mock Authentication Token oluşturucu helper
     */
    private Authentication createAuth(Long customerId, String role) {
        CurrentUser principal = new CurrentUser(customerId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    // =========================================================================
    // CANCEL ORDER AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Cancel Order Authorization Tests")
    class CancelOrderAuthorizationTests {

        @Test
        @DisplayName("Test 5: CUSTOMER -> Cancel own PENDING order -> 200 OK & Stock restored")
        void cancelOrder_CustomerOwnPendingOrder_ShouldSucceed() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 2);

            mockMvc.perform(post("/orders/{orderId}/cancel", order.getId())
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            // Verify DB State
            Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(updatedOrder.getCancelledAt()).isNotNull();

            // Verify Stock Restored
            Product reloadedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
            assertThat(reloadedProduct.getStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("Test 6: CUSTOMER -> Cancel another customer's order -> 404 NOT_FOUND")
        void cancelOrder_CustomerAnotherCustomersOrder_ShouldReturn404() throws Exception {
            Order order = createPendingOrder(customerB, testProduct, 1);

            mockMvc.perform(post("/orders/{orderId}/cancel", order.getId())
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isNotFound());

            // Order query edildi ama müşteri ID uyuşmadığından bulunamadı
            verify(orderRepository, times(1)).findByIdAndCustomerIdWithLock(order.getId(), customerA.getId());

            Order originalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(originalOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("Test 8: OPERATION -> Cancel order -> 403 FORBIDDEN (No DB/Service interaction)")
        void cancelOrder_OperationUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(post("/orders/{orderId}/cancel", order.getId())
                            .with(authentication(createAuth(null, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            // THEN: Security Filter / @PreAuthorize isteği kestiği için
            // cancelOrder servis akışındaki kritik kilitli sorgu HİÇ ÇAĞRILMAMALIDIR!
            verify(orderRepository, never()).findByIdAndCustomerIdWithLock(any(), any());
        }

        @Test
        @DisplayName("Test 9: ADMIN -> Cancel order -> 403 FORBIDDEN (No DB/Service interaction)")
        void cancelOrder_AdminUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(post("/orders/{orderId}/cancel", order.getId())
                            .with(authentication(createAuth(null, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findByIdAndCustomerIdWithLock(any(), any());
        }

        @Test
        @DisplayName("Test 10: Anonymous User -> Cancel order -> 401 UNAUTHORIZED (No DB/Service interaction)")
        void cancelOrder_Anonymous_ShouldReturn401AndNotTouchDatabase() throws Exception {
            // Given: Sistemde mevcut bir order var
            Order order = createPendingOrder(customerA, testProduct, 1);

            // When & Then: Authentication bilgisi olmadan istek atılıyor
            mockMvc.perform(post("/orders/{orderId}/cancel", order.getId()))
                    .andExpect(status().isUnauthorized());

            // CRITICAL CHECK: Kimlik doğrulanmadığı için Security Filter seviyesinde kesilmeli, DB/Service ile etkileşime girilmemeli!
            verify(orderRepository, never()).findByIdAndCustomerIdWithLock(any(), any());
        }
    }

    // =========================================================================
    // CREATE ORDER AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Create Order Authorization Tests")
    class CreateOrderAuthorizationTests {

        @Test
        @DisplayName("Test 1: CUSTOMER -> Create order -> Authorization Succeeded")
        void createOrder_Customer_ShouldAuthorizeSuccessfully() throws Exception {
            CreateOrderRequest request = buildCreateOrderRequest(testProduct.getId(), 2);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Test 2: OPERATION -> Create order -> 403 FORBIDDEN")
        void createOrder_Operation_ShouldReturn403() throws Exception {
            CreateOrderRequest request = buildCreateOrderRequest(testProduct.getId(), 1);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(null, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Test 3: ADMIN -> Create order -> 403 FORBIDDEN")
        void createOrder_Admin_ShouldReturn403() throws Exception {
            CreateOrderRequest request = buildCreateOrderRequest(testProduct.getId(), 1);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(null, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Test 4: Anonymous User -> Create order -> 401 UNAUTHORIZED")
        void createOrder_Anonymous_ShouldReturn401() throws Exception {
            CreateOrderRequest request = buildCreateOrderRequest(testProduct.getId(), 1);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(orderRepository, never()).save(any());
        }
    }

    // =========================================================================
    // DOMAIN RULE EXCEPTION AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Domain Rule Exception Authorization Tests")
    class DomainRuleAuthorizationTests {

        @Test
        @DisplayName("Test 7: CUSTOMER -> Cancel own PAID order -> Authorization succeeds, Domain fails with Exception")
        void cancelOrder_CustomerOwnPaidOrder_ShouldPassAuthAndFailWithBusinessException() throws Exception {
            Order paidOrder = createPendingOrder(customerA, testProduct, 1);
            paidOrder.setStatus(OrderStatus.PAID);
            paidOrder.setPaidAt(Instant.now());
            paidOrder = orderRepository.save(paidOrder);

            mockMvc.perform(post("/orders/{orderId}/cancel", paidOrder.getId())
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isBadRequest());

            verify(orderRepository, times(1)).findByIdAndCustomerIdWithLock(paidOrder.getId(), customerA.getId());
        }
    }

    // =========================================================================
    // GET ORDER BY ID AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Get Order By Id Authorization Tests")
    class GetOrderByIdAuthorizationTests {

        @Test
        @DisplayName("CUSTOMER A -> Get own order -> 200 OK")
        void getOrderById_CustomerOwnOrder_ShouldSucceed() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(get("/orders/{orderId}", order.getId())
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(order.getId()));

            verify(orderRepository, times(1)).findByIdAndCustomerId(order.getId(), customerA.getId());
        }

        @Test
        @DisplayName("CUSTOMER A -> Get Customer B's order -> 404 NOT_FOUND")
        void getOrderById_CustomerAnotherCustomersOrder_ShouldReturn404() throws Exception {
            // Given: Customer B'ye ait sipariş
            Order order = createPendingOrder(customerB, testProduct, 1);

            // When: Customer A istek atıyor
            mockMvc.perform(get("/orders/{orderId}", order.getId())
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isNotFound());

            // Resource Ownership Check: Sorgu atılır ancak customerId eşleşmediği için 404 dönmelidir
            verify(orderRepository, times(1)).findByIdAndCustomerId(order.getId(), customerA.getId());
        }

        @Test
        @DisplayName("OPERATION -> Get order -> 403 FORBIDDEN (No Repository Query)")
        void getOrderById_OperationUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(get("/orders/{orderId}", order.getId())
                            .with(authentication(createAuth(null, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            // Security Interceptor seviyesinde kesilmeli, DB'ye sorgu atılmamalıdır
            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }

        @Test
        @DisplayName("ADMIN -> Get order -> 403 FORBIDDEN (No Repository Query)")
        void getOrderById_AdminUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(get("/orders/{orderId}", order.getId())
                            .with(authentication(createAuth(null, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }

        @Test
        @DisplayName("Anonymous User -> Get order -> 401 UNAUTHORIZED (No Repository Query)")
        void getOrderById_Anonymous_ShouldReturn401AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);

            mockMvc.perform(get("/orders/{orderId}", order.getId()))
                    .andExpect(status().isUnauthorized());

            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }
    }

    // =========================================================================
    // UPDATE SHIPPING ADDRESS AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Update Shipping Address Authorization Tests")
    class UpdateShippingAddressAuthorizationTests {

        @Test
        @DisplayName("CUSTOMER A -> Update own PENDING order address -> 200 OK")
        void updateShippingAddress_CustomerOwnPendingOrder_ShouldSucceed() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);
            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            mockMvc.perform(put("/orders/{orderId}/shipping-address", order.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest))
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingAddress.title").value("Yeni Ev Adresi"));

            verify(orderRepository, times(1)).findByIdAndCustomerId(order.getId(), customerA.getId());
        }

        @Test
        @DisplayName("CUSTOMER A -> Update Customer B's order address -> 404 NOT_FOUND")
        void updateShippingAddress_CustomerAnotherCustomersOrder_ShouldReturn404() throws Exception {
            Order order = createPendingOrder(customerB, testProduct, 1);
            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            mockMvc.perform(put("/orders/{orderId}/shipping-address", order.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest))
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isNotFound());

            verify(orderRepository, times(1)).findByIdAndCustomerId(order.getId(), customerA.getId());
        }

        @Test
        @DisplayName("CUSTOMER A -> Update own PAID order address -> Auth passes, Domain fails with Exception")
        void updateShippingAddress_CustomerOwnPaidOrder_ShouldPassAuthAndFailWithBusinessException() throws Exception {
            // Given: Customer A'ya ait PAID order
            Order paidOrder = createPendingOrder(customerA, testProduct, 1);
            paidOrder.setStatus(OrderStatus.PAID);
            paidOrder.setPaidAt(Instant.now());
            paidOrder = orderRepository.save(paidOrder);

            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            // When & Then: Security yetkilendirmesi geçer, updateShippingAddress() iş kuralı fırlatır
            mockMvc.perform(put("/orders/{orderId}/shipping-address", paidOrder.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest))
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isBadRequest()); // ExceptionHandler'ına göre (ör. 400 Bad Request)

            verify(orderRepository, times(1)).findByIdAndCustomerId(paidOrder.getId(), customerA.getId());
        }

        @Test
        @DisplayName("OPERATION -> Update shipping address -> 403 FORBIDDEN (No Repository Query)")
        void updateShippingAddress_OperationUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);
            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            mockMvc.perform(put("/orders/{orderId}/shipping-address", order.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest))
                            .with(authentication(createAuth(null, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }

        @Test
        @DisplayName("ADMIN -> Update shipping address -> 403 FORBIDDEN (No Repository Query)")
        void updateShippingAddress_AdminUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);
            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            mockMvc.perform(put("/orders/{orderId}/shipping-address", order.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest))
                            .with(authentication(createAuth(null, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }

        @Test
        @DisplayName("Anonymous User -> Update shipping address -> 401 UNAUTHORIZED (No Repository Query)")
        void updateShippingAddress_Anonymous_ShouldReturn401AndNotTouchDatabase() throws Exception {
            Order order = createPendingOrder(customerA, testProduct, 1);
            AddressRequest addressRequest = buildAddressRequest("Yeni Ev Adresi");

            mockMvc.perform(put("/orders/{orderId}/shipping-address", order.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addressRequest)))
                    .andExpect(status().isUnauthorized());

            verify(orderRepository, never()).findByIdAndCustomerId(any(), any());
        }
    }

    // =========================================================================
    // GET MY ORDERS AUTHORIZATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Get My Orders Authorization Tests")
    class GetMyOrdersAuthorizationTests {

        @Test
        @DisplayName("CUSTOMER -> Get my orders -> 200 OK")
        void getMyOrders_Customer_ShouldSucceed() throws Exception {
            mockMvc.perform(get("/orders")
                            .param("page", "0")
                            .param("size", "10")
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk());

            verify(orderRepository, times(1)).findOrderSummariesByCustomerId(eq(customerA.getId()), any());
        }

        @Test
        @DisplayName("OPERATION -> Get my orders -> 403 FORBIDDEN (No Repository Query)")
        void getMyOrders_OperationUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            mockMvc.perform(get("/orders")
                            .with(authentication(createAuth(null, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findOrderSummariesByCustomerId(any(), any());
        }

        @Test
        @DisplayName("ADMIN -> Get my orders -> 403 FORBIDDEN (No Repository Query)")
        void getMyOrders_AdminUser_ShouldReturn403AndNotTouchDatabase() throws Exception {
            mockMvc.perform(get("/orders")
                            .with(authentication(createAuth(null, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            verify(orderRepository, never()).findOrderSummariesByCustomerId(any(), any());
        }

        @Test
        @DisplayName("Anonymous User -> Get my orders -> 401 UNAUTHORIZED (No Repository Query)")
        void getMyOrders_Anonymous_ShouldReturn401AndNotTouchDatabase() throws Exception {
            mockMvc.perform(get("/orders"))
                    .andExpect(status().isUnauthorized());

            verify(orderRepository, never()).findOrderSummariesByCustomerId(any(), any());
        }

        @Test
        @DisplayName("CUSTOMER A -> Get my orders -> Should ONLY return Customer A's orders (Data Isolation Check)")
        void getMyOrders_CustomerA_ShouldOnlyReturnCustomerAOrders() throws Exception {
            // GIVEN:
            // Customer A için 2 adet sipariş
            Order orderA1 = createPendingOrder(customerA, testProduct, 1);
            Order orderA2 = createPendingOrder(customerA, testProduct, 2);

            // Customer B için 1 adet sipariş (Sızmaması gereken veri)
            Order orderB1 = createPendingOrder(customerB, testProduct, 3);

            // WHEN: Customer A olarak GET /orders isteği atılıyor
            mockMvc.perform(get("/orders")
                            .param("page", "0")
                            .param("size", "10")
                            .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    // THEN 1: Sayfalama meta verilerinde toplam eleman sayısı tam olarak 2 olmalı
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    // THEN 2: Dönen sipariş ID'lerinin yalnızca Customer A'ya ait olduğu doğrulanmalı
                    .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                            orderA1.getId(),
                            orderA2.getId()
                    )));

            // DB sorgusunun doğru customerId parametresiyle tetiklendiği doğrulanır
            verify(orderRepository, times(1)).findOrderSummariesByCustomerId(eq(customerA.getId()), any());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Order createPendingOrder(Customer customer, Product product, int quantity) {
        Order order = new Order(
                OrderStatus.PENDING,
                customer,
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                product.getPrice().multiply(BigDecimal.valueOf(quantity)),
                Instant.now()
        );
        order.setShippingAddress(new Address("Ev", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay"));
        order.setBillingAddress(new Address("Fatura", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay"));

        product.decreaseStock(quantity);
        productRepository.save(product);

        return orderRepository.save(order);
    }

    private CreateOrderRequest buildCreateOrderRequest(Long productId, int quantity) {
        AddressRequest address = new AddressRequest("Ev", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay");
        OrderItemRequest item = new OrderItemRequest(productId, quantity);
        return new CreateOrderRequest(List.of(item), address, address);
    }

    private AddressRequest buildAddressRequest(String title) {
        return new AddressRequest(title, "İstanbul", "Kadıköy", "34000", "Türkiye", "Yeni Mahalle No:1", "Daire:2");
    }
}
