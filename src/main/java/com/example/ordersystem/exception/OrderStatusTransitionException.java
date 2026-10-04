package com.example.ordersystem.exception;

import com.example.ordersystem.enums.OrderStatus;
import org.springframework.http.HttpStatus;

public class OrderStatusTransitionException extends BusinessException {
    public OrderStatusTransitionException(Long orderId, OrderStatus currentStatus, OrderStatus targetStatus) {
        super(
                String.format("Order %s cannot transition from %s to %s", orderId, currentStatus, targetStatus),
                HttpStatus.CONFLICT,
                "ORDER_TRANSITION_EXCEPTION"
        );
    }
}
