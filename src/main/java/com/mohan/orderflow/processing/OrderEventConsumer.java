package com.mohan.orderflow.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohan.orderflow.event.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point of the processing side. Deserialization lives here, business logic in
 * {@link OrderEventHandler}. Failures are retried and finally sent to the dead-letter topic
 * (see {@code KafkaConsumerConfig}).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderEventHandler handler;

    public OrderEventConsumer(ObjectMapper objectMapper, OrderEventHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "${app.kafka.order-events-topic}", groupId = "${app.kafka.consumer-group}")
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);
        log.debug("Received {} for order {} (partition {}, offset {})",
                event.eventType(), event.orderId(), record.partition(), record.offset());
        handler.handle(event);
    }
}
