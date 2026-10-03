package com.example.ordersystem.controller;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.service.interfaces.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartControllerAuthorizationIntegrationTest {

    private static final Long CUSTOMER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService; // Service katmanını mock'luyoruz; odak HTTP + Security

    /**
     * CurrentUser ve GrantedAuthority taşıyan Mock Authentication Token oluşturucu helper
     */
    private Authentication createAuth(Long customerId, String role) {
        CurrentUser principal = new CurrentUser(customerId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    @Nested
    @DisplayName("GET /api/v1/cart Authorization Tests")
    class GetCartAuthorizationTests {

        @Test
        @DisplayName("When user has CUSTOMER role, should return 200 OK and own cart")
        void getCart_WhenAuthenticatedAsCustomer_ShouldReturn200() throws Exception {
            // Given
            CartResponse mockResponse = new CartResponse(102L, List.of(), 0);
            when(cartService.getCart(CUSTOMER_ID)).thenReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/cart")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(102))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItemCount").value(0));

            // Principal'dan alınan id ile service çağrıldığını doğrula (Principal Isolation)
            verify(cartService).getCart(CUSTOMER_ID);
            verifyNoMoreInteractions(cartService);
        }

        @Test
        @DisplayName("When user is Anonymous, should return 401 Unauthorized")
        void getCart_WhenAnonymousUser_ShouldReturn401() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("When user has OPERATION role, should return 403 Forbidden (RBAC violation)")
        void getCart_WhenAuthenticatedAsOperation_ShouldReturn403() throws Exception {
            // When & Then
            // Cart endpoint'leri yalnızca CUSTOMER rolüne açıktır.
            mockMvc.perform(get("/api/v1/cart")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(cartService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/cart/items Authorization & Principal Isolation Tests")
    class AddItemAuthorizationTests {

        @Test
        @DisplayName("When user has CUSTOMER role, should accept request and pass authenticated customerId to service")
        void addItem_WhenAuthenticatedAsCustomer_ShouldReturn200AndUseAuthenticatedPrincipalId() throws Exception {
            // Given
            AddCartItemRequest request = new AddCartItemRequest(101L, 2);
            CartResponse mockResponse = new CartResponse(2L, List.of(), 2);

            when(cartService.addItem(any(AddCartItemRequest.class), eq(CUSTOMER_ID))).thenReturn(mockResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/cart/items")
                            .content(objectMapper.writeValueAsString(request))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(2))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.totalItemCount").value(2));

            // Principal Extraction Verification:
            // Controller, customerId'yi request'ten değil,
            // authenticated CurrentUser üzerinden alıp service'e aktarmalı.
            verify(cartService).addItem(eq(request), eq(CUSTOMER_ID));
        }

        @Test
        @DisplayName("When user is Anonymous, should return 401 Unauthorized")
        void addItem_WhenAnonymousUser_ShouldReturn401() throws Exception {
            // Given
            AddCartItemRequest request = new AddCartItemRequest(101L, 1);

            // When & Then
            mockMvc.perform(post("/api/v1/cart/items")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("When user has OPERATION role, should return 403 Forbidden (RBAC violation)")
        void addItem_WhenAuthenticatedAsOperation_ShouldReturn403() throws Exception {
            // Given
            AddCartItemRequest request = new AddCartItemRequest(101L, 1);

            // When & Then
            // Cart endpoint'leri yalnızca CUSTOMER rolüne açıktır.
            mockMvc.perform(post("/api/v1/cart/items")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION")))
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(cartService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/cart/items/{productId} Authorization & Parameter Binding Tests")
    class UpdateItemAuthorizationAndBindingTests {

        @Test
        @DisplayName("When user has CUSTOMER role, should bind PathVariable, Principal customerId, and RequestBody correctly to service")
        void updateItem_WhenAuthenticatedAsCustomer_ShouldReturn200AndBindAllParametersCorrectly() throws Exception {
            // Given
            Long productId = 101L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(5);
            CartResponse mockResponse = new CartResponse(2L, List.of(), 5);

            when(cartService.updateItem(eq(productId), eq(CUSTOMER_ID), any(UpdateCartItemRequest.class)))
                    .thenReturn(mockResponse);

            // When & Then
            mockMvc.perform(patch("/api/v1/cart/items/{productId}", productId)
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER")))
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(2))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.totalItemCount").value(5));

            // Triple Parameter Extraction Verification:
            // Controller'ın 3 farklı kaynaktan gelen veriyi doğru sırada birleştirdiği doğrulanır:
            // 1. customerId -> SecurityContext/Principal
            // 2. productId  -> @PathVariable
            // 3. request    -> @RequestBody
            verify(cartService).updateItem(eq(productId), eq(CUSTOMER_ID), eq(request));
        }

        @Test
        @DisplayName("When user is Anonymous, should return 401 Unauthorized")
        void updateItem_WhenAnonymousUser_ShouldReturn401() throws Exception {
            // Given
            Long productId = 101L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(3);

            // When & Then
            mockMvc.perform(patch("/api/v1/cart/items/{productId}", productId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("When user has OPERATION role, should return 403 Forbidden (RBAC violation)")
        void updateItem_WhenAuthenticatedAsOperation_ShouldReturn403() throws Exception {
            // Given
            Long productId = 101L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(3);

            // When & Then
            mockMvc.perform(patch("/api/v1/cart/items/{productId}", productId)
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION")))
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(cartService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/cart/items/{productId} Authorization & Binding Tests")
    class RemoveItemAuthorizationTests {

        @Test
        @DisplayName("When user has CUSTOMER role, should bind PathVariable productId and Principal customerId without request body")
        void removeItem_WhenAuthenticatedAsCustomer_ShouldReturn200AndPassCorrectParameters() throws Exception {
            // Given
            Long productId = 101L;
            CartResponse mockResponse = new CartResponse(2L, List.of(), 0);

            when(cartService.removeItem(eq(productId), eq(CUSTOMER_ID))).thenReturn(mockResponse);

            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items/{productId}", productId)
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(2))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.totalItemCount").value(0));

            // Dual Parameter Binding Verification:
            // 1. customerId -> SecurityContext Principal
            // 2. productId  -> @PathVariable
            verify(cartService).removeItem(eq(productId), eq(CUSTOMER_ID));
        }

        @Test
        @DisplayName("When user is Anonymous, should return 401 Unauthorized")
        void removeItem_WhenAnonymousUser_ShouldReturn401() throws Exception {
            // Given
            Long productId = 101L;

            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items/{productId}", productId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("When user has OPERATION role, should return 403 Forbidden (RBAC violation)")
        void removeItem_WhenAuthenticatedAsOperation_ShouldReturn403() throws Exception {
            // Given
            Long productId = 101L;

            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items/{productId}", productId)
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(cartService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/cart/items Authorization Tests (Clear Cart)")
    class ClearCartAuthorizationTests {

        @Test
        @DisplayName("When user has CUSTOMER role, should execute clearCart with authenticated customerId and return 200 OK")
        void clearCart_WhenAuthenticatedAsCustomer_ShouldReturn200AndPassAuthenticatedCustomerId() throws Exception {
            // Given: Mock response (cartId = null/non-null, items = [], totalItemCount = 0)
            Long mockCartId = 2L;
            CartResponse mockResponse = new CartResponse(mockCartId, List.of(), 0);

            when(cartService.clearCart(eq(CUSTOMER_ID))).thenReturn(mockResponse);

            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").value(mockCartId))
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItemCount").value(0));

            // Principal Isolation Verification:
            // cartService.clearCart çağrılırken parametrenin dışarıdan alınan bir id değil,
            // SecurityContext'teki authenticated customerId olduğunu doğruluyoruz.
            verify(cartService).clearCart(eq(CUSTOMER_ID));
        }

        @Test
        @DisplayName("When user is Anonymous, should return 401 Unauthorized")
        void clearCart_WhenAnonymousUser_ShouldReturn401() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("When user has OPERATION role, should return 403 Forbidden (RBAC violation)")
        void clearCart_WhenAuthenticatedAsOperation_ShouldReturn403() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/cart/items")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(cartService);
        }
    }
}
