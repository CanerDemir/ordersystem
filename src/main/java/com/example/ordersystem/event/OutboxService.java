package com.example.ordersystem.event;

import com.example.ordersystem.entity.OutboxEvent;
import com.example.ordersystem.enums.AggregateType;
import com.example.ordersystem.enums.EventType;
import com.example.ordersystem.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final EventSerializer eventSerializer;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentSucceeded(PaymentSucceededEvent event) {
        String payload = eventSerializer.serialize(event);

        OutboxEvent outboxEvent = OutboxEvent.createWithEventId(
                event.eventId(),
                EventType.PAYMENT_SUCCEEDED,
                AggregateType.PAYMENT,
                event.paymentId(),
                payload
        );

        outboxEventRepository.save(outboxEvent);
    }
}
