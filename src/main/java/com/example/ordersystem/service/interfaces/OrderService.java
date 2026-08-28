package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.auth.CurrentUser;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, CurrentUser user);
}
