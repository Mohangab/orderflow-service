package com.mohan.orderflow.processing;

import com.mohan.orderflow.config.ProcessingProperties;
import com.mohan.orderflow.event.OrderEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPaymentSimulatorTest {

    private final InventoryPaymentSimulator simulator = new InventoryPaymentSimulator(
            new ProcessingProperties(new BigDecimal("100.00"), 10, Set.of("SKU-GONE")));

    @Test
    void approvesOrderWithinLimits() {
        assertThat(simulator.evaluate(event("50.00", new OrderEvent.Line("SKU-A", 2))).approved()).isTrue();
    }

    @Test
    void rejectsOutOfStockSku() {
        ProcessingDecision decision = simulator.evaluate(event("10.00", new OrderEvent.Line("SKU-GONE", 1)));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("SKU-GONE is out of stock");
    }

    @Test
    void rejectsQuantityAboveStock() {
        ProcessingDecision decision = simulator.evaluate(event("10.00", new OrderEvent.Line("SKU-A", 11)));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).contains("insufficient stock");
    }

    @Test
    void declinesPaymentAboveLimit() {
        ProcessingDecision decision = simulator.evaluate(event("100.01", new OrderEvent.Line("SKU-A", 1)));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).startsWith("Payment");
    }

    private static OrderEvent event(String total, OrderEvent.Line... lines) {
        return new OrderEvent(UUID.randomUUID(), "OrderCreated", UUID.randomUUID(), "c",
                new BigDecimal(total), List.of(lines), null, Instant.now());
    }
}
