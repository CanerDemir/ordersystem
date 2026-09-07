package com.example.ordersystem.controller;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;
import com.example.ordersystem.service.interfaces.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/{orderId}/payments")
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request,
            @AuthenticatedUser CurrentUser user) {
        PaymentResponse response = paymentService.processOrderPayment(orderId, request, user);
        return ResponseEntity.ok(response);
    }
}
