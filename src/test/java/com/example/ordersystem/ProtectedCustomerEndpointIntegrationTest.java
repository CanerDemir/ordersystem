package com.example.ordersystem;

import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.auth.JwtService;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Role;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.RoleRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Test izolasyonu: Her test metodu sonunda DB otomatik rollback edilir
class ProtectedCustomerEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Customer customerUser;
    private Customer adminUser;
    private String customerJwtToken;
    private String adminJwtToken;

    @BeforeEach
    void setUp() {
        // Roller hazırlığı
        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        // 1. Müşteri Kullanıcısı (ROLE_CUSTOMER)
        customerUser = new Customer(
                "Caner",
                "Demir",
                "caner.protected@example.com",
                "654654163516",
                passwordEncoder.encode("password123")
        );
        customerUser.assignRole(roleCustomer);
        customerUser = customerRepository.save(customerUser);

        CustomUserDetails customerDetails = new CustomUserDetails(
                customerUser.getId(),
                customerUser.getEmail(),
                customerUser.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        );
        customerJwtToken = jwtService.generateToken(customerDetails);

        // 2. Admin Kullanıcısı (ROLE_ADMIN)
        adminUser = new Customer(
                "Admin",
                "User",
                "admin.protected@example.com",
                "654654163516",
                passwordEncoder.encode("password123")
        );
        adminUser.assignRole(roleAdmin);
        adminUser = customerRepository.save(adminUser);

        CustomUserDetails adminDetails = new CustomUserDetails(
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true
        );
        adminJwtToken = jwtService.generateToken(adminDetails);
    }

    @Nested
    @DisplayName("GET /api/v1/customers/me - Authenticated Endpoint Tests")
    class GetCurrentCustomerProfileTests {

        @Test
        @DisplayName("Geçerli JWT Token ile korumalı endpoint'e erişim başarılı olmalı (200 OK)")
        void shouldAccessProtectedEndpointWithValidJwt() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("caner.protected@example.com"))
                    .andExpect(jsonPath("$.firstName").value("Caner"))
                    .andExpect(jsonPath("$.lastName").value("Demir"));
        }

        @Test
        @DisplayName("401 Unauthorized: Authorization header eksik veya JWT geçersiz olduğunda")
        void shouldReturn401WhenJwtIsMissingOrInvalid() throws Exception {
            // Case 1: Header hiç yok
            mockMvc.perform(get("/api/v1/customers/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            // Case 2: Geçersiz Token
            mockMvc.perform(get("/api/v1/customers/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/dashboard - 401 vs 403 Role Authorization Tests")
    class RoleAuthorizationTests {

        @Test
        @DisplayName("403 Forbidden: Doğrulanmış kullanıcı (ROLE_CUSTOMER) yetkisi olmayan endpoint'e erişmek istediğinde")
        void shouldReturn403WhenUserLacksRequiredRole() throws Exception {
            // Müşteri kimliği doğrulanmış (Authenticated), ancak ROLE_ADMIN yetkisine sahip değil -> 403 Forbidden
            mockMvc.perform(get("/api/v1/admin/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 Unauthorized vs 403 Forbidden Farkı: Yetkisiz çağrıda 401, rol eksikliğinde 403 alınmalı")
        void verifyDifferenceBetween401And403() throws Exception {
            // Token YOK -> Kimlik doğrulanamadı (401 Unauthorized)
            mockMvc.perform(get("/api/v1/admin/dashboard")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            // Token VAR ama Rol Yetersiz -> Kimlik doğrulandı ama Yetkisiz (403 Forbidden)
            mockMvc.perform(get("/api/v1/admin/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            // Token VAR ve Rol Uyumlu -> Başarılı (200 OK)
            mockMvc.perform(get("/api/v1/admin/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwtToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }
}