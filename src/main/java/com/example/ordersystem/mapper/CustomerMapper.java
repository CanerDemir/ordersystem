package com.example.ordersystem.mapper;

import com.example.ordersystem.dto.response.CustomerResponse;
import com.example.ordersystem.entity.Customer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CustomerMapper {
    public CustomerResponse toCustomerResponse(Customer customer) {
        if (customer == null) return null;
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone()
        );
    }

    public List<CustomerResponse> toCustomerResponseList(List<Customer> customers) {
        if (customers == null) return Collections.emptyList();
        return customers.stream().map(this::toCustomerResponse).toList();
    }
}
