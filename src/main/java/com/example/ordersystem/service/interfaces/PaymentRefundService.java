package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.RefundRequest;
import com.example.ordersystem.dto.response.RefundResponse;

public interface PaymentRefundService {

    RefundResponse processRefund(Long orderId, Long customerId, RefundRequest request);
}
