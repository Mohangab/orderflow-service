package com.mohan.orderflow.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    /** Retry a failing record 3 times (1s apart), then park it on {@code <topic>.DLT}. */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
        // Poison messages will never succeed - skip retries and go straight to the DLT.
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

    @Bean
    public NewTopic orderEventsDeadLetterTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().orderEventsTopic() + ".DLT")
                .partitions(props.kafka().partitions())
                .replicas(1)
                .build();
    }
}
