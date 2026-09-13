package com.example.ordersystem.service.interfaces;


import com.example.ordersystem.dto.response.CustomerResponse;

import java.util.List;

public interface CustomerService {
    CustomerResponse getCustomerById(Long id);
    List<CustomerResponse> getAllCustomers();
    void deleteCustomer(Long id);
}
