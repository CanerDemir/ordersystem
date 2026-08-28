package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.CustomerCreateRequest;
import com.example.ordersystem.dto.response.CustomerResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.service.interfaces.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new RuntimeException("The customer already exists");
        }

        Customer customer = new Customer(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.password()
        );
        Customer savedCustomer = customerRepository.save(customer);
        return new CustomerResponse(
                savedCustomer.getId(),
                savedCustomer.getFirstName(),
                savedCustomer.getLastName(),
                savedCustomer.getEmail(),
                savedCustomer.getPhone()
        );
    }
}
