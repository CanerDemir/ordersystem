package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.CustomerCreateRequest;
import com.example.ordersystem.dto.response.CustomerResponse;

public interface CustomerService {
    CustomerResponse createCustomer(CustomerCreateRequest request);
}
