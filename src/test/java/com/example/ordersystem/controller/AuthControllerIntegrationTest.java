package com.example.ordersystem.controller;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecretKey;

    @Nested
    @DisplayName("POST /api/v1/auth/register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Test 1: Müşteri başarıyla kaydolmalı (201 Created), şifre hash'lenmeli ve ROLE_CUSTOMER atanmalı")
        void shouldRegisterCustomerSuccessfully() throws Exception {
            Map<String, String> request = Map.of(
                    "firstName", "Caner",
                    "lastName", "Demir",
                    "email", "caner@example.com",
                    "phone", "5551234567",
                    "password", "password123"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId").exists())
                    .andExpect(jsonPath("$.email").value("caner@example.com"))
                    .andExpect(jsonPath("$.firstName").value("Caner"))
                    .andExpect(jsonPath("$.lastName").value("Demir"));

            // Veritabanı Doğrulamaları
            Customer customer = customerRepository.findByEmailWithRoles("caner@example.com")
                    .orElseThrow(() -> new AssertionError("Müşteri veritabanında bulunamadı!"));

            // 1. Password plain-text değil ve BCrypt hash doğrulaması başarılı
            assertThat(customer.getPassword()).isNotEqualTo("password123");
            assertThat(passwordEncoder.matches("password123", customer.getPassword())).isTrue();

            // 2. ROLE_CUSTOMER ilişkisi kurulmuş
            boolean hasCustomerRole = customer.getCustomerRoles().stream()
                    .anyMatch(cr -> cr.getRole().getName().equals("ROLE_CUSTOMER"));
            assertThat(hasCustomerRole).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login Tests")
    class LoginTests {

        @BeforeEach
        void setUpUser() throws Exception {
            // Her login testi öncesinde Caner kullanıcısını kaydediyoruz
            Map<String, String> registerRequest = Map.of(
                    "firstName", "Caner",
                    "lastName", "Demir",
                    "email", "caner@example.com",
                    "phone", "5551234567",
                    "password", "password123"
            );

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Test 3 & 6: Doğru bilgilerle giriş (200 OK) ve JWT Claim zincirinin doğrulanması")
        void shouldLoginSuccessfullyAndVerifyJwtClaims() throws Exception {
            Map<String, String> loginRequest = Map.of(
                    "email", "caner@example.com",
                    "password", "password123"
            );

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andReturn();

            // Response'tan Access Token okuma
            String responseString = result.getResponse().getContentAsString();
            Map<?, ?> responseMap = objectMapper.readValue(responseString, Map.class);
            String accessToken = (String) responseMap.get("accessToken");

            // JWT Claim Analizi
            SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken).getPayload();

            // Claim Doğrulamaları
            assertThat(claims.getSubject()).isEqualTo("caner@example.com");

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            assertThat(roles).isNotNull();
            assertThat(roles).contains("ROLE_CUSTOMER");

            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
        }
    }
}
