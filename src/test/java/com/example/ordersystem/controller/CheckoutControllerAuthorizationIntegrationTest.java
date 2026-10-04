package com.example.ordersystem.controller;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CheckoutRequest;
import com.example.ordersystem.entity.Cart;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.repository.CartRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CheckoutControllerAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    private CheckoutRequest request;
    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();

        testCustomer = customerRepository.save(
                new Customer("Caner", "Demir", "caner@example.com", "+905551112233", "encoded_password")
        );

        testProduct = productRepository.save(
                Product.create("Klavyeli Kılıf", new BigDecimal("100.00"), 10, "Açıklama 1")
        );

        Cart cart = new Cart(testCustomer);
        cart.addProduct(testProduct, 2);
        cartRepository.save(cart);

        AddressRequest address = new AddressRequest("Ev", "İstanbul", "Kadıköy", "34000", "Türkiye", "Açık adres", "Detay");
        request = new CheckoutRequest(address, address);
    }

    /**
     * CurrentUser ve GrantedAuthority taşıyan Mock Authentication Token oluşturucu helper
     */
    private Authentication createAuth(Long customerId, String role) {
        CurrentUser principal = new CurrentUser(customerId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("POST /api/v1/checkout Authorization tests")
    class CheckoutAuthorizationTests {

        @Test
        @DisplayName("Test 1: CUSTOMER -> Checkout -> Authorization Succeeded")
        void checkout_Customer_ShouldAuthorizeSuccessfully() throws Exception {

            mockMvc.perform(post("/api/v1/checkout")
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(testCustomer.getId(), "ROLE_CUSTOMER"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"));

            // --- DB STATE VERIFICATIONS ---
            // 1. Sipariş oluşmuş olmalı
            assertThat(orderRepository.count()).isEqualTo(1);

            // 2. Stok düşmüş olmalı (10 - 2 = 8)
            Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
            assertThat(updatedProduct.getStock()).isEqualTo(8);

            // 3. Sepet temizlenmiş olmalı
            Cart updatedCart = cartRepository.findByCustomer_Id(testCustomer.getId()).orElseThrow();
            assertThat(updatedCart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("Test 2: OPERATION -> Checkout -> 403 FORBIDDEN")
        void checkout_Operation_ShouldReturn403() throws Exception {

            mockMvc.perform(post("/api/v1/checkout")
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(testCustomer.getId(), "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            assertDatabaseStateUnchanged();
        }

        @Test
        @DisplayName("Test 3: ADMIN -> Checkout -> 403 FORBIDDEN")
        void checkout_Admin_ShouldReturn403() throws Exception {

            mockMvc.perform(post("/api/v1/checkout")
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(testCustomer.getId(), "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            assertDatabaseStateUnchanged();
        }

        @Test
        @DisplayName("Test 4: Anonymous User -> Checkout -> 401 UNAUTHORIZED")
        void checkout_Anonymous_ShouldReturn401() throws Exception {

            mockMvc.perform(post("/api/v1/checkout")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            assertDatabaseStateUnchanged();
        }
    }

    private void assertDatabaseStateUnchanged() {
        // 1. Hiçbir sipariş oluşmamış olmalı
        assertThat(orderRepository.count()).isZero();

        // 2. Stok aynı kalmalı (10)
        Product currentProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(currentProduct.getStock()).isEqualTo(10);

        // 3. Sepetteki ürün ve miktar aynı kalmalı (2)
        Cart currentCart = cartRepository.findByCustomer_Id(testCustomer.getId()).orElseThrow();
        assertThat(currentCart.getItems()).hasSize(1);
        assertThat(currentCart.getItems().getFirst().getQuantity()).isEqualTo(2);
    }
}
