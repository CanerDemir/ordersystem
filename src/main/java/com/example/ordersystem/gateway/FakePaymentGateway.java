package com.example.ordersystem.gateway;

import com.example.ordersystem.dto.PaymentExecutionDto;
import com.example.ordersystem.dto.PaymentResultDto;
import com.example.ordersystem.dto.RefundExecutionDto;
import com.example.ordersystem.dto.RefundResultDto;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Setter
@Component
public class FakePaymentGateway implements PaymentGateway {
    public static final String FAIL_KEY_PREFIX = "FAIL_";
    public static final String REFUND_FAIL_KEY_PREFIX = "REFUND_FAIL_";
    private final AtomicInteger paymentCallCount = new AtomicInteger(0);
    private final AtomicInteger refundCallCount = new AtomicInteger(0);

    @Override
    public PaymentResultDto processPayment(PaymentExecutionDto executionDto) {
        paymentCallCount.incrementAndGet();
        if (executionDto.idempotencyKey() != null && executionDto.idempotencyKey().startsWith(FAIL_KEY_PREFIX)) {
            return PaymentResultDto.failure("Payment rejected by fake gateway test rule.");
        }

        String transactionRef = "TX-FAKE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return PaymentResultDto.success(transactionRef);
    }

    @Override
    public RefundResultDto refundPayment(RefundExecutionDto executionDto) {
        refundCallCount.incrementAndGet();
        if (executionDto.idempotencyKey() != null && executionDto.idempotencyKey().startsWith(REFUND_FAIL_KEY_PREFIX)) {
            return RefundResultDto.failure("Refund processing failed due to test scenario.");
        }

        String refundReference = "RF-FAKE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return RefundResultDto.success(refundReference);
    }

    public int getPaymentCallCount() {
        return paymentCallCount.get();
    }

    public int getRefundCallCount() {
        return refundCallCount.get();
    }

    public void resetCallCount() {
        paymentCallCount.set(0);
        refundCallCount.set(0);
    }
}
