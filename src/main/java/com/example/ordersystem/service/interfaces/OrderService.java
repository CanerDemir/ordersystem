package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request, CurrentUser user);
    OrderResponse getOrderById(Long orderId, CurrentUser user);
    OrderResponse cancelOrder(Long orderId, CurrentUser user);
    Page<OrderSummaryResponse> getCustomerOrders(CurrentUser user, Pageable pageable);
    OrderResponse updateShippingAddress(Long orderId, AddressRequest request, CurrentUser user);
}
