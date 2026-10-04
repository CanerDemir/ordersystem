package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.CheckoutRequest;
import com.example.ordersystem.dto.response.OrderResponse;

public interface CheckoutService {
    OrderResponse checkout(CheckoutRequest request, Long customerId);
}
