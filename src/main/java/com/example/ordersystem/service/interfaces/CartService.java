package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(Long customerId);
    CartResponse addItem(AddCartItemRequest request, Long customerId);
    CartResponse updateItem(Long customerId, Long productId, UpdateCartItemRequest request);
    CartResponse removeItem(Long productId, Long customerId);
    CartResponse clearCart(Long customerId);
}
