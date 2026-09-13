package com.example.ordersystem;

import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.auth.JwtService;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Role;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.RoleRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Test İzolasyonu: Her test sonrasında DB Rollback edilir
class MethodLevelSecurityIntegrationTest {

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

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    private Customer customerA;
    private Customer customerB;
    private Customer adminUser;
    private Customer operationUser;

    private String customerAToken;
    private String customerBToken;
    private String adminToken;
    private String operationToken;

    @BeforeEach
    void setUp() {
        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        Role roleOperation = roleRepository.findByName("ROLE_OPERATION")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_OPERATION")));

        // Customer A
        customerA = createAndSaveCustomer("customerA@example.com", "Customer", "A", roleCustomer);
        customerAToken = jwtService.generateToken(new CustomUserDetails(
                customerA.getId(),
                customerA.getEmail(),
                customerA.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        ));

        // Customer B
        customerB = createAndSaveCustomer("customerB@example.com", "Customer", "B", roleCustomer);
        customerBToken = jwtService.generateToken(new CustomUserDetails(
                customerB.getId(),
                customerB.getEmail(),
                customerB.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        ));

        // Admin User
        adminUser = createAndSaveCustomer("admin@example.com", "Admin", "User", roleAdmin);
        adminToken = jwtService.generateToken(new CustomUserDetails(
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true
        ));

        // Operation User
        operationUser = createAndSaveCustomer("operation@example.com", "Ops", "User", roleOperation);
        operationToken = jwtService.generateToken(new CustomUserDetails(
                operationUser.getId(),
                operationUser.getEmail(),
                operationUser.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_OPERATION")),
                true
        ));
    }

    private Customer createAndSaveCustomer(String email, String firstName, String lastName, Role role) {
        Customer customer = new Customer(firstName, lastName, email, "36546846315", passwordEncoder.encode("password123"));
        customer.assignRole(role);
        return customerRepository.save(customer);
    }

    @Nested
    @DisplayName("GET /api/v1/customers/me Tests")
    class CustomerMeEndpointTests {

        @Test
        @DisplayName("CUSTOMER + valid JWT -> 200")
        void customerWithValidJwt_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("customerA@example.com"));
        }

        @Test
        @DisplayName("ADMIN + valid JWT -> 200")
        void adminWithValidJwt_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("admin@example.com"));
        }

        @Test
        @DisplayName("No JWT -> 401")
        void noJwt_ShouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Invalid JWT -> 401")
        void invalidJwt_ShouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/customers/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/customers/{id} Ownership Tests")
    class CustomerOwnershipEndpointTests {

        @Test
        @DisplayName("CUSTOMER -> kendi ID -> 200")
        void customerAccessingOwnId_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("customerA@example.com"));
        }

        @Test
        @DisplayName("CUSTOMER -> başka ID -> 403 Forbidden")
        void customerAccessingOtherId_ShouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerB.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerAToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN -> başka ID -> 200 OK")
        void adminAccessingAnyCustomer_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("customerA@example.com"));
        }

        @Test
        @DisplayName("No JWT -> 401 Unauthorized")
        void noJwt_ShouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerA.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("OPERATION -> Kendi Customer ID'si ile dahi istek atsa -> 403 Forbidden")
        void operationRole_AccessingOwnCustomerId_ShouldReturn403() throws Exception {
            // operationUser nesnesinin ID'si ile istek atılıyor
            mockMvc.perform(get("/api/v1/customers/" + operationUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERATION -> Başka Customer ID'si ile istek atsa -> 403 Forbidden")
        void operationRole_AccessingOtherCustomerId_ShouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerA.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Admin-Only Customer Management Tests (GET /api/v1/customers)")
    class AdminOnlyEndpointTests {

        @Test
        @DisplayName("CUSTOMER -> 403")
        void customerAccessingAdminEndpoint_ShouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/customers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerAToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OPERATION -> 403")
        void operationAccessingAdminEndpoint_ShouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/customers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN -> 200")
        void adminAccessingAdminEndpoint_ShouldReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/customers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("No JWT -> 401")
        void noJwt_ShouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Critical Security Test: DB Authority Source Validation")
    class CanonicalAuthoritySourceSecurityTests {

        @Test
        @DisplayName("JWT içinde ROLE_ADMIN olsa bile DB'de ROLE_CUSTOMER olan kullanıcı Admin endpoint'e erişememeli (403)")
        void jwtWithForgedAdminRole_ShouldBeRejectedBasedOnDbRoles() throws Exception {
            // Given: DB'de sadece ROLE_CUSTOMER yetkisi olan müşteri kullanıcısı (customerA)
            // Fake Token: JWT Claim'ine sahte olarak "ROLE_ADMIN" ekleniyor.
            Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey));
            String forgedAdminJwtToken = Jwts.builder()
                    .claims(Map.of("roles", List.of("ROLE_ADMIN")))
                    .subject(customerA.getEmail()) // email = customerA@example.com
                    .issuedAt(new Date(System.currentTimeMillis()))
                    .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                    .signWith(key)
                    .compact();

            // When & Then: Admin-only bir metoda sahte admin token'ı ile istek atılır
            mockMvc.perform(get("/api/v1/customers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedAdminJwtToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden()); // 403 Forbidden beklenir
        }
    }
}