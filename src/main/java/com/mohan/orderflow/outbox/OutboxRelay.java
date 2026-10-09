package com.mohan.orderflow.outbox;

import com.mohan.orderflow.config.AppProperties;
import com.mohan.orderflow.domain.OutboxEvent;
import com.mohan.orderflow.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls unpublished outbox rows and relays them to Kafka (at-least-once).
 * <p>
 * Rows are only marked published after the broker acknowledged the write. If Kafka is
 * unavailable the batch stops and is retried on the next tick; a crash between "sent" and
 * "marked" can cause a duplicate, which the idempotent consumer tolerates.
 */
@Component
public class OutboxRelay {

    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_EVENT_ID = "eventId";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties props;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       AppProperties props) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}")
    @Transactional
    public int relayPendingEvents() {
        List<OutboxEvent> pending = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(toRecord(event))
                        .get(props.outbox().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished();
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {} ({}); will retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                break; // keep per-order ordering: never skip ahead of a failed event
            }
        }
        if (published > 0) {
            log.debug("Relayed {} outbox event(s) to Kafka", published);
        }
        return published;
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                props.kafka().orderEventsTopic(), event.getAggregateId().toString(), event.getPayload());
        record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HEADER_EVENT_ID, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
