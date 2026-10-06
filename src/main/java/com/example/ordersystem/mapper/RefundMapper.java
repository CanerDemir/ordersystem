package com.example.ordersystem.mapper;

import com.example.ordersystem.dto.response.RefundResponse;
import com.example.ordersystem.entity.PaymentRefund;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundResponse toRefundResponse(PaymentRefund paymentRefund) {
        return new RefundResponse(
                paymentRefund.getId(),
                paymentRefund.getPaymentId(),
                paymentRefund.getOrderId(),
                paymentRefund.getAmount(),
                paymentRefund.getStatus(),
                paymentRefund.getProviderRefundReference(),
                paymentRefund.getFailureReason(),
                paymentRefund.getCreatedAt(),
                paymentRefund.getCompletedAt()
        );
    }
}
