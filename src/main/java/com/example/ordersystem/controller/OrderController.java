package com.example.ordersystem.controller;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.dto.response.OrderSummaryResponse;
import com.example.ordersystem.service.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.getOrderById(orderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.cancelOrder(orderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<OrderSummaryResponse>> getMyOrders(@AuthenticatedUser CurrentUser currentUser, @PageableDefault(page = 0, size = 20)Pageable pageable) {
        Page<OrderSummaryResponse> orders = orderService.getCustomerOrders(currentUser, pageable);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{orderId}/shipping-address")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> updateShippingAddress(@PathVariable Long orderId, @Valid @RequestBody AddressRequest request, @AuthenticatedUser CurrentUser currentUser) {
        OrderResponse response = orderService.updateShippingAddress(orderId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/prepare")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<OrderResponse> startPreparing(@PathVariable Long orderId) {
        OrderResponse response = orderService.startPreparing(orderId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/ship")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<OrderResponse> markAsShipped(@PathVariable Long orderId) {
        OrderResponse response = orderService.markAsShipped(orderId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/deliver")
    @PreAuthorize("hasRole('OPERATION')")
    public ResponseEntity<OrderResponse> markAsDelivered(@PathVariable Long orderId) {
        OrderResponse response = orderService.markAsDelivered(orderId);
        return ResponseEntity.ok(response);
    }
}
