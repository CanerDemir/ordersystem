package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.auth.JwtService;
import com.example.ordersystem.dto.request.LoginRequest;
import com.example.ordersystem.dto.request.RegisterRequest;
import com.example.ordersystem.dto.response.LoginResponse;
import com.example.ordersystem.dto.response.RegisterResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.CustomerRole;
import com.example.ordersystem.entity.Role;
import com.example.ordersystem.exception.CustomerAlreadyExistsException;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<Customer> customerCaptor;

    private RegisterRequest registerRequest;
    private Role customerRole;
    private LoginRequest validLoginRequest;
    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
                "Caner",
                "Demir",
                "caner@example.com",
                "5551234567",
                "password123"
        );

        customerRole = new Role("ROLE_CUSTOMER");
        ReflectionTestUtils.setField(customerRole, "id", 1L);

        validLoginRequest = new LoginRequest("caner@example.com", "password123");

        Customer customer = new Customer(
                "Caner",
                "Demir",
                "caner@example.com",
                "5551234567",
                "$2a$10$encodedPasswordHash"
        );
        customer.assignRole(new Role("ROLE_CUSTOMER"));

        customUserDetails = new CustomUserDetails(
                11L,
                customer.getEmail(),
                customer.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER")),
                true
        );
    }

    @Nested
    @DisplayName("register() Tests")
    class RegisterTests {

        @Test
        @DisplayName("Test 1 Başarılı kayıt: Müşteri şifresi encode edilmeli, ROLE_CUSTOMER atanmalı ve kaydolmalı")
        void shouldRegisterCustomerSuccessfully() {
            // Given
            given(customerRepository.existsByEmail(registerRequest.email())).willReturn(false);
            given(roleRepository.findByName("ROLE_CUSTOMER")).willReturn(Optional.of(customerRole));
            given(passwordEncoder.encode("password123")).willReturn("$2a$10$encodedPasswordHash");

            given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> {
                Customer savedCustomer = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedCustomer, "id", 1L);
                return savedCustomer;
            });

            // When
            RegisterResponse response = authService.register(registerRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.customerId()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("caner@example.com");
            assertThat(response.firstName()).isEqualTo("Caner");
            assertThat(response.lastName()).isEqualTo("Demir");

            // Repository'ye kaydedilmek üzere gönderilen Customer nesnesinin doğrulanması (ArgumentCaptor)
            verify(customerRepository).save(customerCaptor.capture());
            Customer capturedCustomer = customerCaptor.getValue();

            assertThat(capturedCustomer.getEmail()).isEqualTo("caner@example.com");
            assertThat(capturedCustomer.getPassword()).isEqualTo("$2a$10$encodedPasswordHash");

            Set<CustomerRole>  capturedCustomerRoles = capturedCustomer.getCustomerRoles();
            assertThat(capturedCustomerRoles).hasSize(1);
            assertThat(capturedCustomerRoles.stream().findFirst().get().getRole().getName()).isEqualTo("ROLE_CUSTOMER");

            verify(customerRepository, times(1)).existsByEmail(registerRequest.email());
            verify(roleRepository, times(1)).findByName("ROLE_CUSTOMER");
            verify(passwordEncoder, times(1)).encode("password123");
            verify(customerRepository, times(1)).save(any(Customer.class));
        }

        @Test
        @DisplayName("Test 2 Mevcut email ile kayıt denemesinde CustomerAlreadyExistsException fırlatılmalı")
        void shouldThrowExceptionWhenEmailAlreadyExists() {
            // Given
            given(customerRepository.existsByEmail(registerRequest.email())).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(CustomerAlreadyExistsException.class)
                    .hasMessageContaining("Customer with email caner@example.com already exists.");

            verify(customerRepository, times(1)).existsByEmail(registerRequest.email());
            verifyNoInteractions(roleRepository);
            verifyNoInteractions(passwordEncoder);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("DB'de ROLE_CUSTOMER bulunamadığında IllegalStateException fırlatılmalı")
        void shouldThrowExceptionWhenDefaultRoleNotFound() {
            // Given
            given(customerRepository.existsByEmail(registerRequest.email())).willReturn(false);
            given(roleRepository.findByName("ROLE_CUSTOMER")).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Default role 'ROLE_CUSTOMER' not found in the database.");

            verify(customerRepository, times(1)).existsByEmail(registerRequest.email());
            verify(roleRepository, times(1)).findByName("ROLE_CUSTOMER");
            verifyNoInteractions(passwordEncoder);
            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("login() Tests")
    class LoginTests {

        @Test
        @DisplayName("Test 3 & 6: Doğru bilgilerle giriş yapıldığında JWT access token dönmeli")
        void shouldLoginSuccessfullyAndReturnJwtToken() {
            // Given
            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(customUserDetails);
            given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .willReturn(authentication);
            given(jwtService.generateToken(customUserDetails)).willReturn("mocked.jwt.token");

            // When
            LoginResponse response = authService.login(validLoginRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");

            // AuthenticationManager çağrısının parametre doğrulaması
            verify(authenticationManager, times(1)).authenticate(
                    argThat(auth ->
                            auth.getPrincipal().equals("caner@example.com") &&
                                    auth.getCredentials().equals("password123")
                    )
            );
            verify(jwtService, times(1)).generateToken(customUserDetails);
        }

        @Test
        @DisplayName("Test 4 & 5: Hatalı şifre veya olmayan email durumunda BadCredentialsException fırlatılmalı")
        void shouldThrowExceptionWhenAuthenticationFails() {
            // Given
            LoginRequest invalidRequest = new LoginRequest("caner@example.com", "wrong-password");
            given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .willThrow(new BadCredentialsException("Invalid credentials"));

            // When & Then
            assertThatThrownBy(() -> authService.login(invalidRequest))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid credentials");

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Principal CustomUserDetails tipinde değilse IllegalStateException fırlatılmalı")
        void shouldThrowIllegalStateExceptionWhenPrincipalIsNotCustomUserDetails() {
            // Given
            Authentication authentication = mock(Authentication.class);
            UserDetails standardUserDetails = mock(UserDetails.class); // CustomUserDetails değil!
            given(authentication.getPrincipal()).willReturn(standardUserDetails);

            given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .willReturn(authentication);

            // When & Then
            assertThatThrownBy(() -> authService.login(validLoginRequest))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Authentication principal must be an instance of CustomUserDetails");

            verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verifyNoInteractions(jwtService);
        }
    }
}
