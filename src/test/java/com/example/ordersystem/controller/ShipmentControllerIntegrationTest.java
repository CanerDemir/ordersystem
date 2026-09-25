package com.example.ordersystem.controller;

import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.auth.JwtService;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.entity.Role;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ShipmentStatus;
import com.example.ordersystem.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class ShipmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private JwtService jwtTokenProvider;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    // Fixture identifiers & tokens
    private String tokenCustomerA;
    private String tokenCustomerB;
    private String tokenOperation;
    private String tokenAdmin;

    private Order orderCustomerA;
    private Order orderCustomerB;
    private Order orderCustomerAWithoutShipment;
    private Shipment shipmentCustomerA;

    @BeforeEach
    void setUp() {

        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        Role roleOperation = roleRepository.findByName("ROLE_OPERATION")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_OPERATION")));

        // 1. Create Users & Customers
        Customer customerA = new Customer("Caner", "Demir", "customer_a@test.com", "123456789", "password123");
        customerA.assignRole(roleCustomer);
        customerRepository.save(customerA);

        Customer customerB = new Customer("Ali", "Veli", "customer_b@test.com", "123456789", "password123");
        customerB.assignRole(roleCustomer);
        customerRepository.save(customerB);

        Customer userOperation = new Customer("operation_user", "opop", "op@test.com","1234568", "password123");
        userOperation.assignRole(roleOperation);
        customerRepository.save(userOperation);

        Customer adminUser = new Customer("admin_user", "adU", "admin@test.com", "123456789", "password123");
        adminUser.assignRole(roleAdmin);
        customerRepository.save(adminUser);

        // 2. Generate Real JWT Tokens
        tokenCustomerA = "Bearer " + jwtTokenProvider.generateToken(new CustomUserDetails(
                customerA.getId(),
                customerA.getEmail(),
                customerA.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        ));
        tokenCustomerB = "Bearer " + jwtTokenProvider.generateToken(new CustomUserDetails(
                customerB.getId(),
                customerB.getEmail(),
                customerB.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        ));
        tokenOperation = "Bearer " + jwtTokenProvider.generateToken(new CustomUserDetails(
                userOperation.getId(),
                userOperation.getEmail(),
                userOperation.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_OPERATION")),
                true
        ));
        tokenAdmin = "Bearer " + jwtTokenProvider.generateToken(new CustomUserDetails(
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true
        ));

        // 3. Create Orders (PAID)
        orderCustomerA = new Order(OrderStatus.PAID, customerA, customerA.getPhone(), customerA.getFirstName(), customerA.getLastName(), customerA.getEmail(), BigDecimal.valueOf(150.00), Instant.now());
        orderCustomerB = new Order(OrderStatus.PAID, customerB, customerB.getPhone(), customerB.getFirstName(), customerB.getLastName(), customerB.getEmail(), BigDecimal.valueOf(1000.00), Instant.now());
        orderCustomerAWithoutShipment = new Order(OrderStatus.PAID, customerA, customerA.getPhone(), customerA.getFirstName(), customerA.getLastName(), customerA.getEmail(), BigDecimal.valueOf(150.00), Instant.now());
        orderRepository.save(orderCustomerA);
        orderRepository.save(orderCustomerB);
        orderRepository.save(orderCustomerAWithoutShipment);

        // 4. Create Shipments (READY)
        shipmentCustomerA = shipmentRepository.save(Shipment.createReady(orderCustomerA));
        shipmentRepository.save(Shipment.createReady(orderCustomerB));
    }

    // =========================================================================
    // 1. GET /api/v1/orders/{orderId}/shipment
    // =========================================================================
    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}/shipment Tests")
    class GetShipmentByOrderIdTests {

        @Test
        @DisplayName("1. CUSTOMER - Kendi order'ının kargo bilgisini başarıyla getirmeli (200 OK)")
        void shouldReturnShipmentWhenCustomerRequestsOwnOrder() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(shipmentCustomerA.getId().intValue())))
                    .andExpect(jsonPath("$.orderId", is(orderCustomerA.getId().intValue())))
                    .andExpect(jsonPath("$.status", is("READY")));
        }

        @Test
        @DisplayName("2. CUSTOMER - Başka bir customer'ın orderId'sini istediğinde IDOR engellenmeli (403 Forbidden)")
        void shouldReturn403WhenCustomerRequestsAnotherCustomersOrder() throws Exception {
            // Customer A -> Customer B'ye ait orderId ile istek atıyor
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerB.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("3. OPERATION - Endpoint'e erişim hakkı olmamalı (403 Forbidden)")
        void shouldReturn403WhenOperationUserRequestsShipment() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("4. ADMIN - Endpoint'e erişim hakkı olmamalı (403 Forbidden)")
        void shouldReturn403WhenAdminUserRequestsShipment() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5. Anonymous - Token olmadan istek atıldığında kimlik doğrulama başarısız olmalı (401 Unauthorized)")
        void shouldReturn401WhenAnonymousUserRequestsShipment() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("6. CUSTOMER - Kendi order'ı için henüz shipment oluşturulmamışsa 404 Not Found dönmeli")
        void shouldReturn404WhenShipmentDoesNotExistForOwnOrder() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderCustomerAWithoutShipment.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // 3. PATCH /api/v1/shipments/{shipmentId}/ship
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/v1/shipments/{shipmentId}/ship Tests")
    class ShipShipmentTests {

        @Test
        @DisplayName("1. OPERATION - READY durumundaki shipment'ı başarıyla SHIPPED yapmalı (200 OK + DB Persistence Check)")
        void shouldShipShipmentSuccessfullyWhenOperationUser() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(shipmentCustomerA.getId().intValue())))
                    .andExpect(jsonPath("$.orderId", is(orderCustomerA.getId().intValue())))
                    .andExpect(jsonPath("$.trackingNumber", is("TR123456789")))
                    .andExpect(jsonPath("$.carrier", is("Yurtici")))
                    .andExpect(jsonPath("$.status", is("SHIPPED")))
                    .andExpect(jsonPath("$.shippedAt", notNullValue()));

            // L1 cache'i temizleyip güncel state'i DB'den sorguluyoruz
            entityManager.flush();
            entityManager.clear();

            Shipment updatedShipment = shipmentRepository.findById(shipmentCustomerA.getId())
                    .orElseThrow(() -> new AssertionError("Shipment DB'de bulunamadı!"));

            assertThat(updatedShipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(updatedShipment.getTrackingNumber()).isEqualTo("TR123456789");
            assertThat(updatedShipment.getCarrier()).isEqualTo("Yurtici");
            assertThat(updatedShipment.getShippedAt()).isNotNull();
        }

        @Test
        @DisplayName("2. CUSTOMER - Shipment kargolama yetkisi olmamalı (403 Forbidden)")
        void shouldReturn403WhenCustomerTriesToShipShipment() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("3. ADMIN - Shipment kargolama yetkisi olmamalı (403 Forbidden)")
        void shouldReturn403WhenAdminTriesToShipShipment() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("4. Anonymous - Token olmadan istek atıldığında kimlik doğrulama başarısız olmalı (401 Unauthorized)")
        void shouldReturn401WhenAnonymousUserTriesToShipShipment() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("5. Invalid State - Zaten SHIPPED olan shipment tekrar ship edilmeye çalışıldığında 400 dönmeli")
        void shouldReturn400WhenShipmentIsAlreadyShipped() throws Exception {
            // Önce kargoyu SHIPPED durumuna çekiyoruz
            shipmentCustomerA.markAsShipped("TR123456789", "Yurtici");
            shipmentRepository.save(shipmentCustomerA);

            String requestBody = """
                    {
                        "trackingNumber": "TR987654321",
                        "carrier": "Aras"
                    }
                    """;

            // Zaten SHIPPED olan bir kargoya tekrar ship isteği atıldığında IllegalStateException/BusinessException fırlatılır
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest()); // Global Exception Handler mapping'ine göre 400 Bad Request
        }

        @Test
        @DisplayName("6. Validation - trackingNumber blank gönderildiğinde 400 Bad Request dönmeli")
        void shouldReturn400WhenTrackingNumberIsBlank() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "   ",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("7. Validation - carrier null gönderildiğinde 400 Bad Request dönmeli")
        void shouldReturn400WhenCarrierIsNull() throws Exception {
            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": null
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("8. Resource Not Found - Var olmayan shipmentId ile istek atıldığında 404 Not Found dönmeli")
        void shouldReturn404WhenShipmentDoesNotExist() throws Exception {
            Long nonExistentShipmentId = 999999L;

            String requestBody = """
                    {
                        "trackingNumber": "TR123456789",
                        "carrier": "Yurtici"
                    }
                    """;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/ship", nonExistentShipmentId)
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // 4. PATCH /api/v1/shipments/{shipmentId}/transit
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/v1/shipments/{shipmentId}/transit Tests")
    class MoveToInTransitShipmentTests {

        @Test
        @DisplayName("1. OPERATION - SHIPPED durumundaki shipment'ı başarıyla IN_TRANSIT yapmalı (204 No Content + DB Persistence Check)")
        void shouldMoveShipmentToInTransitSuccessfully() throws Exception {
            // Given: Shipment önce SHIPPED durumuna getiriliyor
            shipmentCustomerA.markAsShipped("TR123456789", "Yurtici");
            shipmentRepository.save(shipmentCustomerA);

            // When
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNoContent());

            // Then: DB Persistence Verification
            entityManager.flush();
            entityManager.clear();

            Shipment updatedShipment = shipmentRepository.findById(shipmentCustomerA.getId())
                    .orElseThrow(() -> new AssertionError("Shipment DB'de bulunamadı!"));

            assertThat(updatedShipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("2. Invalid State - READY durumundaki shipment doğrudan IN_TRANSIT yapılamaz (400 Bad Request)")
        void shouldReturn400WhenMovingToTransitFromReadyState() throws Exception {
            // shipmentCustomerA varsayılan olarak READY durumunda
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("3. CUSTOMER - In-Transit durumuna geçirme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenCustomerTriesToMoveToTransit() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("4. ADMIN - In-Transit durumuna geçirme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenAdminTriesToMoveToTransit() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5. Anonymous - Token olmadan istek atıldığında 401 Unauthorized dönmeli")
        void shouldReturn401WhenAnonymousUserTriesToMoveToTransit() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", shipmentCustomerA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("6. Resource Not Found - Var olmayan shipmentId ile transit isteği atıldığında 404 Not Found dönmeli")
        void shouldReturn404WhenShipmentDoesNotExist() throws Exception {
            Long nonExistentShipmentId = 999999L;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/transit", nonExistentShipmentId)
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // 5. PATCH /api/v1/shipments/{shipmentId}/deliver
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/v1/shipments/{shipmentId}/deliver Tests")
    class DeliverShipmentTests {

        @Test
        @DisplayName("1. OPERATION - IN_TRANSIT durumundaki shipment'ı başarıyla DELIVERED yapmalı (204 No Content + deliveredAt Check)")
        void shouldDeliverShipmentSuccessfully() throws Exception {
            // Given: Shipment önce SHIPPED, ardından IN_TRANSIT durumuna getiriliyor
            shipmentCustomerA.markAsShipped("TR123456789", "Yurtici");
            shipmentCustomerA.markAsInTransit();
            shipmentRepository.save(shipmentCustomerA);

            var originalShippedAt = shipmentCustomerA.getShippedAt();

            // When
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNoContent());

            // Then: DB Persistence Verification
            entityManager.flush();
            entityManager.clear();

            Shipment updatedShipment = shipmentRepository.findById(shipmentCustomerA.getId())
                    .orElseThrow(() -> new AssertionError("Shipment DB'de bulunamadı!"));

            assertThat(updatedShipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(updatedShipment.getDeliveredAt()).isNotNull();
            assertThat(updatedShipment.getShippedAt()).isEqualTo(originalShippedAt);
        }

        @Test
        @DisplayName("2. Invalid State - READY durumundaki shipment doğrudan DELIVERED yapılamaz (400 Bad Request)")
        void shouldReturn400WhenDeliveringDirectlyFromReadyState() throws Exception {
            // shipmentCustomerA varsayılan olarak READY durumunda
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("3. CUSTOMER - Teslim etme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenCustomerTriesToDeliverShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("4. ADMIN - Teslim etme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenAdminTriesToDeliverShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5. Anonymous - Token olmadan istek atıldığında 401 Unauthorized dönmeli")
        void shouldReturn401WhenAnonymousUserTriesToDeliverShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", shipmentCustomerA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("6. Resource Not Found - Var olmayan shipmentId ile deliver isteği atıldığında 404 Not Found dönmeli")
        void shouldReturn404WhenShipmentDoesNotExist() throws Exception {
            Long nonExistentShipmentId = 999999L;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/deliver", nonExistentShipmentId)
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // 6. PATCH /api/v1/shipments/{shipmentId}/cancel
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/v1/shipments/{shipmentId}/cancel Tests")
    class CancelShipmentTests {

        @Test
        @DisplayName("1. OPERATION - READY durumundaki shipment'ı başarıyla CANCELLED yapmalı (204 No Content + Timestamps Null Check)")
        void shouldCancelShipmentSuccessfullyFromReadyState() throws Exception {
            // shipmentCustomerA varsayılan olarak READY durumunda
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNoContent());

            // DB Persistence Verification
            entityManager.flush();
            entityManager.clear();

            Shipment updatedShipment = shipmentRepository.findById(shipmentCustomerA.getId())
                    .orElseThrow(() -> new AssertionError("Shipment DB'de bulunamadı!"));

            assertThat(updatedShipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(updatedShipment.getShippedAt()).isNull();
            assertThat(updatedShipment.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("2. Invalid State - SHIPPED durumundaki shipment iptal edilemez (400 Bad Request)")
        void shouldReturn400WhenCancellingShippedShipment() throws Exception {
            // Given: Shipment SHIPPED durumuna getiriliyor
            shipmentCustomerA.markAsShipped("TR123456789", "Yurtici");
            shipmentRepository.save(shipmentCustomerA);

            // When & Then
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("3. Invalid State - IN_TRANSIT durumundaki shipment iptal edilemez (400 Bad Request)")
        void shouldReturn400WhenCancellingInTransitShipment() throws Exception {
            // Given: Shipment IN_TRANSIT durumuna getiriliyor
            shipmentCustomerA.markAsShipped("TR123456789", "Yurtici");
            shipmentCustomerA.markAsInTransit();
            shipmentRepository.save(shipmentCustomerA);

            // When & Then
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("4. CUSTOMER - İptal etme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenCustomerTriesToCancelShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenCustomerA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5. ADMIN - İptal etme yetkisine sahip olmamalı (403 Forbidden)")
        void shouldReturn403WhenAdminTriesToCancelShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, tokenAdmin))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("6. Anonymous - Token olmadan istek atıldığında 401 Unauthorized dönmeli")
        void shouldReturn401WhenAnonymousUserTriesToCancelShipment() throws Exception {
            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", shipmentCustomerA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("7. Resource Not Found - Var olmayan shipmentId ile cancel isteği atıldığında 404 Not Found dönmeli")
        void shouldReturn404WhenShipmentDoesNotExist() throws Exception {
            Long nonExistentShipmentId = 999999L;

            mockMvc.perform(patch("/api/v1/shipments/{shipmentId}/cancel", nonExistentShipmentId)
                            .header(HttpHeaders.AUTHORIZATION, tokenOperation))
                    .andExpect(status().isNotFound());
        }
    }
}