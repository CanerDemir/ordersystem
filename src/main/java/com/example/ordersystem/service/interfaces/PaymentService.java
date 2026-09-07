package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.PaymentRequest;
import com.example.ordersystem.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse processOrderPayment(Long orderId, PaymentRequest request, CurrentUser currentUser);
}
