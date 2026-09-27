package com.example.ordersystem.auth;

import com.example.ordersystem.entity.Address;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    OrderRepository orderRepository;

    @MockitoSpyBean
    private ShipmentRepository shipmentRepository;

    private Customer customerA;
    private Order orderAWithShipment;
    private Order orderAWithoutShipment;
    private Order orderBWithShipment;

    @BeforeEach
    void setUp() {
        customerA = customerRepository.save(new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "password1"));
        Customer customerB = customerRepository.save(new Customer("Ahmet", "Yılmaz", "ahmet@example.com", "+905554445566", "password2"));
        orderAWithShipment = createOrder(customerA);
        Shipment shipmentA = shipmentRepository.save(Shipment.createReady(orderAWithShipment));
        orderAWithoutShipment = createOrder(customerA);
        orderBWithShipment = createOrder(customerB);
        Shipment shipmentB = shipmentRepository.save(Shipment.createReady(orderBWithShipment));
    }

    /**
     * CurrentUser ve GrantedAuthority taşıyan Mock Authentication Token oluşturucu helper
     */
    private Authentication createAuth(Long customerId, String role) {
        CurrentUser principal = new CurrentUser(customerId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Test
    @DisplayName("1. CUSTOMER A kendi order'ının shipment'ını isteyince 200 OK dönmeli")
    void givenCustomerA_whenGetOwnShipment_thenReturn200() throws Exception {

        mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderAWithShipment.getId())
                        .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                .andExpect(status().isOk());

        verify(shipmentRepository)
                .findByOrderIdAndOrderCustomerId(orderAWithShipment.getId(), customerA.getId());
    }

    @Test
    @DisplayName("2. CUSTOMER A başkasının shipment'ını isteyince 404 NOT_FOUND dönmeli ve sorgu atılmalı")
    void givenCustomerA_whenGetOtherCustomerShipment_thenReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderBWithShipment.getId())
                        .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());

        // Single query test: Sadece müşteri ID'si eklenmiş ownership sorgusu çağrılmalı
        verify(shipmentRepository)
                .findByOrderIdAndOrderCustomerId(orderBWithShipment.getId(), customerA.getId());
    }

    @Test
    @DisplayName("3. OPERATION rolü bu endpoint'e erişmeye çalışınca 403 FORBIDDEN dönmeli ve DB'ye gidilmemeli")
    @WithMockUser(roles = "OPERATION")
    void givenOperationRole_whenGetShipment_thenReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/999999/shipment"))
                .andExpect(status().isForbidden());

        verify(shipmentRepository, never())
                .findByOrderIdAndOrderCustomerId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("4. ADMIN rolü bu endpoint'e erişmeye çalışınca 403 FORBIDDEN dönmeli ve DB'ye gidilmemeli")
    @WithMockUser(roles = "ADMIN")
    void givenAdminRole_whenGetShipment_thenReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/999999/shipment"))
                .andExpect(status().isForbidden());

        verify(shipmentRepository, never())
                .findByOrderIdAndOrderCustomerId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("5. Anonymous kullanıcı erişmeye çalışınca 401 UNAUTHORIZED dönmeli ve DB'ye gidilmemeli")
    void givenAnonymousUser_whenGetShipment_thenReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/10/shipment"))
                .andExpect(status().isUnauthorized());

        verify(shipmentRepository, never())
                .findByOrderIdAndOrderCustomerId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("6. CUSTOMER kendi order'ını ister ama henüz shipment oluşturulmamışsa 404 NOT_FOUND dönmeli")
    void givenCustomerA_whenOrderHasNoShipment_thenReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/shipment", orderAWithoutShipment.getId())
                        .with(authentication(createAuth(customerA.getId(), "ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());

        verify(shipmentRepository)
                .findByOrderIdAndOrderCustomerId(orderAWithoutShipment.getId(), customerA.getId());
    }

    // Helper Methods
    private Order createOrder(Customer customer) {
        Order order = new Order(
                OrderStatus.PAID,
                customer,
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                new BigDecimal("100.00"),
                Instant.now()
        );
        order.setShippingAddress(new Address("Ev", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay"));
        order.setBillingAddress(new Address("Fatura", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay"));

        return orderRepository.save(order);
    }
}