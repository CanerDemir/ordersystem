package com.example.ordersystem.mapper;

import com.example.ordersystem.dto.response.CartItemResponse;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.entity.Cart;
import com.example.ordersystem.entity.CartItem;
import com.example.ordersystem.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {

    public CartItemResponse toCartItemResponse(CartItem item) {
        Product product = item.getProduct();
        return new CartItemResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                item.subtotal()
        );
    }

    public CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream().map(this::toCartItemResponse).toList();

        return new CartResponse(
                cart.getId(),
                itemResponses,
                cart.totalItemCount()
        );
    }
}
