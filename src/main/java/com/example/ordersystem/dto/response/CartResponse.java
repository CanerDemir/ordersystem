package com.example.ordersystem.dto.response;

import java.util.List;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        Integer totalItemCount
) {
}
