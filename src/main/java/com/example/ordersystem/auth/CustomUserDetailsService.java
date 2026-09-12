package com.example.ordersystem.auth;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Customer customer = customerRepository.findByEmailWithRoles(email).orElseThrow(() -> new UsernameNotFoundException("User cannot be found with email: " + email));
        return mapToCustomUserDetails(customer);
    }

    private CustomUserDetails mapToCustomUserDetails(Customer customer) {
        Set<GrantedAuthority> authorities = customer.getCustomerRoles().stream()
                .map(customerRole -> new SimpleGrantedAuthority(customerRole.getRole().getName()))
                .collect(Collectors.toSet());
        return new CustomUserDetails(
                customer.getId(),
                customer.getEmail(),
                customer.getPassword(),
                authorities,
                true
        );
    }
}
