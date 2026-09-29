package com.example.ordersystem.controller;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.service.interfaces.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerAuthorizationIntegrationTest {

    private static final Long CUSTOMER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductCreateRequest validCreateRequest;
    private ProductUpdateRequest validUpdateRequest;
    private ProductResponse dummyProductResponse;

    @BeforeEach
    void setUp() {
        validCreateRequest = new ProductCreateRequest("Test Product", BigDecimal.valueOf(100.00), 10, "Test product description");

        validUpdateRequest = new ProductUpdateRequest("Updated Product", BigDecimal.valueOf(150.00), 20, "Updated product description");

        dummyProductResponse = new ProductResponse(
                1L,
                "Test Product",
                BigDecimal.valueOf(100.00),
                10,
                "Test product description",
                ProductStatus.ACTIVE,
                1L,
                Instant.now(),
                Instant.now()
        );

        // Service mock stubbing
        when(productService.create(any(ProductCreateRequest.class))).thenReturn(dummyProductResponse);
        when(productService.getById(eq(1L))).thenReturn(dummyProductResponse);
        when(productService.getAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(dummyProductResponse)));
        when(productService.update(eq(1L), any(ProductUpdateRequest.class))).thenReturn(dummyProductResponse);
        doNothing().when(productService).deactivate(eq(1L));
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
    // POST /api/v1/products
    // =========================================================================
    @Nested
    @DisplayName("POST /api/v1/products - Create Authorization")
    class CreateAuthorization {

        @Test
        @DisplayName("Anonymous user -> 401 UNAUTHORIZED + Service çağrılmamalı")
        void create_whenAnonymous_shouldReturn401AndNotCallService() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest)))
                    .andExpect(status().isUnauthorized());

            verify(productService, never()).create(any());
        }

        @Test
        @DisplayName("CUSTOMER -> 403 FORBIDDEN + Service çağrılmamalı")
        void create_whenCustomer_shouldReturn403AndNotCallService() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());

            verify(productService, never()).create(any());
        }

        @Test
        @DisplayName("OPERATION -> 201 CREATED + Service tam 1 kez çağrılmalı")
        void create_whenOperation_shouldReturn201() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isCreated());

            verify(productService).create(any(ProductCreateRequest.class));
        }

        @Test
        @DisplayName("ADMIN -> 201 CREATED + Service tam 1 kez çağrılmalı")
        void create_whenAdmin_shouldReturn201() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_ADMIN"))))
                    .andExpect(status().isCreated());

            verify(productService).create(any(ProductCreateRequest.class));
        }
    }

    // =========================================================================
    // PUT /api/v1/products/{productId}
    // =========================================================================
    @Nested
    @DisplayName("PUT /api/v1/products/{productId} - Update Authorization")
    class UpdateAuthorization {

        @Test
        @DisplayName("Anonymous user -> 401 UNAUTHORIZED + Service çağrılmamalı")
        void update_whenAnonymous_shouldReturn401AndNotCallService() throws Exception {
            mockMvc.perform(put("/api/v1/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest)))
                    .andExpect(status().isUnauthorized());

            verify(productService, never()).update(anyLong(), any());
        }

        @Test
        @DisplayName("CUSTOMER -> 403 FORBIDDEN + Service çağrılmamalı")
        void update_whenCustomer_shouldReturn403AndNotCallService() throws Exception {
            mockMvc.perform(put("/api/v1/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());

            verify(productService, never()).update(anyLong(), any());
        }

        @Test
        @DisplayName("OPERATION -> 200 OK + Service tam 1 kez çağrılmalı")
        void update_whenOperation_shouldReturn200() throws Exception {
            mockMvc.perform(put("/api/v1/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isOk());

            verify(productService).update(eq(1L), any(ProductUpdateRequest.class));
        }

        @Test
        @DisplayName("ADMIN -> 200 OK + Service tam 1 kez çağrılmalı")
        void update_whenAdmin_shouldReturn200() throws Exception {
            mockMvc.perform(put("/api/v1/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest))
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            verify(productService).update(eq(1L), any(ProductUpdateRequest.class));
        }
    }

    // =========================================================================
    // PATCH /api/v1/products/{productId}/deactivate
    // =========================================================================
    @Nested
    @DisplayName("PATCH /api/v1/products/{productId}/deactivate - Deactivate Authorization")
    class DeactivateAuthorization {

        @Test
        @DisplayName("Anonymous user -> 401 UNAUTHORIZED + Service çağrılmamalı")
        void deactivate_whenAnonymous_shouldReturn401AndNotCallService() throws Exception {
            mockMvc.perform(patch("/api/v1/products/1/deactivate"))
                    .andExpect(status().isUnauthorized());

            verify(productService, never()).deactivate(anyLong());
        }

        @Test
        @DisplayName("CUSTOMER -> 403 FORBIDDEN + Service çağrılmamalı")
        void deactivate_whenCustomer_shouldReturn403AndNotCallService() throws Exception {
            mockMvc.perform(patch("/api/v1/products/1/deactivate")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());

            verify(productService, never()).deactivate(anyLong());
        }

        @Test
        @DisplayName("OPERATION -> 204 NO_CONTENT + Service tam 1 kez çağrılmalı")
        void deactivate_whenOperation_shouldReturn204() throws Exception {
            mockMvc.perform(patch("/api/v1/products/1/deactivate")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isNoContent());

            verify(productService).deactivate(1L);
        }

        @Test
        @DisplayName("ADMIN -> 204 NO_CONTENT + Service tam 1 kez çağrılmalı")
        void deactivate_whenAdmin_shouldReturn204() throws Exception {
            mockMvc.perform(patch("/api/v1/products/1/deactivate")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_ADMIN"))))
                    .andExpect(status().isNoContent());

            verify(productService).deactivate(1L);
        }
    }

    // =========================================================================
    // GET /api/v1/products/{productId}
    // =========================================================================
    @Nested
    @DisplayName("GET /api/v1/products/{productId} - Read Authorization")
    class GetByIdAuthorization {

        @Test
        @DisplayName("Anonymous user -> 200 OK")
        void getById_whenAnonymous_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk());

            verify(productService).getById(1L);
        }

        @Test
        @DisplayName("CUSTOMER -> 200 OK")
        void getById_whenCustomer_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products/1")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk());

            verify(productService).getById(1L);
        }

        @Test
        @DisplayName("OPERATION -> 200 OK")
        void getById_whenOperation_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products/1")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isOk());

            verify(productService).getById(1L);
        }

        @Test
        @DisplayName("ADMIN -> 200 OK")
        void getById_whenAdmin_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products/1")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            verify(productService).getById(1L);
        }
    }

    // =========================================================================
    // GET /api/v1/products
    // =========================================================================
    @Nested
    @DisplayName("GET /api/v1/products - List Authorization")
    class GetAllAuthorization {

        @Test
        @DisplayName("Anonymous user -> 200 OK")
        void getAll_whenAnonymous_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk());

            verify(productService).getAll(any(Pageable.class));
        }

        @Test
        @DisplayName("CUSTOMER -> 200 OK")
        void getAll_whenCustomer_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_CUSTOMER"))))
                    .andExpect(status().isOk());

            verify(productService).getAll(any(Pageable.class));
        }

        @Test
        @DisplayName("OPERATION -> 200 OK")
        void getAll_whenOperation_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_OPERATION"))))
                    .andExpect(status().isOk());

            verify(productService).getAll(any(Pageable.class));
        }

        @Test
        @DisplayName("ADMIN -> 200 OK")
        void getAll_whenAdmin_shouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .with(authentication(createAuth(CUSTOMER_ID, "ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            verify(productService).getAll(any(Pageable.class));
        }
    }
}