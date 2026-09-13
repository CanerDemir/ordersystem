package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.auth.JwtService;
import com.example.ordersystem.dto.request.LoginRequest;
import com.example.ordersystem.dto.request.RegisterRequest;
import com.example.ordersystem.dto.response.LoginResponse;
import com.example.ordersystem.dto.response.RegisterResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Role;
import com.example.ordersystem.enums.UserRole;
import com.example.ordersystem.exception.CustomerAlreadyExistsException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.RoleRepository;
import com.example.ordersystem.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new CustomerAlreadyExistsException(request.email());
        }

        Role defaultRole = roleRepository.findByName(UserRole.Constants.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default role 'ROLE_CUSTOMER' not found in the database."));

        String encodedPassword = passwordEncoder.encode(request.password());

        Customer customer = new Customer(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                encodedPassword
        );
        customer.assignRole(defaultRole);
        Customer savedCustomer = customerRepository.save(customer);
        return new RegisterResponse(
                savedCustomer.getId(),
                savedCustomer.getFirstName(),
                savedCustomer.getLastName(),
                savedCustomer.getEmail()
        );
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("Authentication principal must be an instance of CustomUserDetails");
        }

        String token = jwtService.generateToken(userDetails);

        return new LoginResponse(token);
    }
}
