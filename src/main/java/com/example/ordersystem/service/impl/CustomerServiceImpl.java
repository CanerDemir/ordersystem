package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.response.CustomerResponse;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.CustomerMapper;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.service.interfaces.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return customerMapper.toCustomerResponse(customer);
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return customerMapper.toCustomerResponseList(customers);
    }

    @Override
    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }
}
