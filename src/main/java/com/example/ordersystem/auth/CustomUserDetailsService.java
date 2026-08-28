package com.example.ordersystem.auth;

import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final CustomerRepository customerRepository;

    public CustomUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Customer customer = customerRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User cannot be found with email: " + email));
        return mapToCustomUserDetails(customer);
    }

    private CustomUserDetails mapToCustomUserDetails(Customer customer) {
        return new CustomUserDetails(
                customer.getId(),
                customer.getEmail(),
                customer.getPassword(),
                true
        );
    }
}
