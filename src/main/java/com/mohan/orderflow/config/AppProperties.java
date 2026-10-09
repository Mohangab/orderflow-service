package com.mohan.orderflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Kafka kafka,
        @DefaultValue Outbox outbox
) {

    public record Kafka(
            @DefaultValue("orders.events") String orderEventsTopic,
            @DefaultValue("3") int partitions
    ) {
    }

    public record Outbox(
            @DefaultValue("1s") Duration pollInterval,
            @DefaultValue("5s") Duration sendTimeout
    ) {
    }
}
