package com.mohan.orderflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Rules for the simulated inventory + payment checks performed by the order processor.
 */
@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(
        @DefaultValue("5000.00") BigDecimal maxOrderAmount,
        @DefaultValue("100") int maxQuantityPerSku,
        @DefaultValue Set<String> outOfStockSkus
) {
}
