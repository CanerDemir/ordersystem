package com.example.ordersystem.gateway;

import com.example.ordersystem.dto.PaymentExecutionDto;
import com.example.ordersystem.dto.PaymentResultDto;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Setter
@Component
public class FakePaymentGateway implements PaymentGateway {
    public static final String FAIL_KEY_PREFIX = "FAIL_";
    private final AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public PaymentResultDto processPayment(PaymentExecutionDto executionDto) {
        callCount.incrementAndGet();
        if (executionDto.idempotencyKey() != null && executionDto.idempotencyKey().startsWith(FAIL_KEY_PREFIX)) {
            return new PaymentResultDto(false, null, "Payment rejected by fake gateway test rule.");
        }

        String transactionRef = "TX-FAKE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResultDto(true, transactionRef, null);
    }

    public int getCallCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }
}
