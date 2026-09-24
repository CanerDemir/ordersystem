package com.example.ordersystem.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.consumer.topic}")
    private String mainTopicName;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(mainTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(mainTopicName + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}