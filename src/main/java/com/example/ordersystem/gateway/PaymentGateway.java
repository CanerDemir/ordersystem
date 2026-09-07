package com.example.ordersystem.gateway;

import com.example.ordersystem.dto.PaymentExecutionDto;
import com.example.ordersystem.dto.PaymentResultDto;

public interface PaymentGateway {
    PaymentResultDto processPayment(PaymentExecutionDto executionDto);
}
