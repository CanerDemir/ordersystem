package com.example.ordersystem.controller;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.service.interfaces.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> getCart(@AuthenticatedUser CurrentUser currentUser) {
        CartResponse response = cartService.getCart(currentUser.customerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request, @AuthenticatedUser CurrentUser currentUser) {
        CartResponse response = cartService.addItem(request,  currentUser.customerId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable("productId") Long productId,
            @AuthenticatedUser CurrentUser currentUser,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartResponse response = cartService.updateItem(productId, currentUser.customerId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> removeItem(@PathVariable("productId")  Long productId, @AuthenticatedUser CurrentUser currentUser) {
        CartResponse response = cartService.removeItem(productId, currentUser.customerId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> clearCart(@AuthenticatedUser CurrentUser currentUser) {
        CartResponse response = cartService.clearCart(currentUser.customerId());
        return ResponseEntity.ok(response);
    }
}
