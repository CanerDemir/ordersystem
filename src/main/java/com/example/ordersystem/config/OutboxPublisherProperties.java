package com.example.ordersystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "outbox.publisher")
public class OutboxPublisherProperties {

    /**
     * Single batch claim size. Default: 100
     */
    private int batchSize = 100;

    /**
     * Lease expiration duration in seconds. Default: 60
     */
    private long leaseDurationSeconds = 60;

    /**
     * Fixed delay between execution cycles in milliseconds. Default: 1000
     */
    private long fixedDelayMs = 1000;

    private String topic = "order-events";

    private int publishTimeoutSeconds = 5;
}