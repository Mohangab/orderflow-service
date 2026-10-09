package com.mohan.orderflow.processing;

import com.mohan.orderflow.config.ProcessingProperties;
import com.mohan.orderflow.event.OrderEvent;
import org.springframework.stereotype.Component;

/**
 * Stand-in for downstream inventory and payment services. Pure and deterministic so
 * the business rules are easy to unit test:
 * <ul>
 *     <li>any SKU listed in {@code app.processing.out-of-stock-skus} is rejected</li>
 *     <li>more than {@code max-quantity-per-sku} units of a SKU is rejected</li>
 *     <li>an order total above {@code max-order-amount} is declined by "payment"</li>
 * </ul>
 */
@Component
public class InventoryPaymentSimulator {

    private final ProcessingProperties props;

    public InventoryPaymentSimulator(ProcessingProperties props) {
        this.props = props;
    }

    public ProcessingDecision evaluate(OrderEvent event) {
        for (OrderEvent.Line line : event.items()) {
            if (props.outOfStockSkus().contains(line.sku())) {
                return ProcessingDecision.reject("Inventory: " + line.sku() + " is out of stock");
            }
            if (line.quantity() > props.maxQuantityPerSku()) {
                return ProcessingDecision.reject("Inventory: insufficient stock for " + line.sku());
            }
        }
        if (event.totalAmount().compareTo(props.maxOrderAmount()) > 0) {
            return ProcessingDecision.reject("Payment: amount " + event.totalAmount()
                    + " exceeds limit " + props.maxOrderAmount());
        }
        return ProcessingDecision.approve();
    }
}
