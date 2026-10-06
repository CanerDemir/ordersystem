package com.example.ordersystem.gateway;

import com.example.ordersystem.dto.PaymentExecutionDto;
import com.example.ordersystem.dto.PaymentResultDto;
import com.example.ordersystem.dto.RefundExecutionDto;
import com.example.ordersystem.dto.RefundResultDto;

public interface PaymentGateway {
    PaymentResultDto processPayment(PaymentExecutionDto executionDto);
    RefundResultDto refundPayment(RefundExecutionDto executionDto);
}
