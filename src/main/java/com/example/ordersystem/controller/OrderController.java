package com.example.ordersystem.controller;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.service.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.createOrder(createOrderRequest, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.getOrderById(orderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.cancelOrder(orderId, currentUser);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
