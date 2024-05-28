package br.com.microservices.orchestrated.orderservice.core.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.topic.start-saga}")
    private String topic;

    public void sendEvent(String payload) {
        try {
            kafkaTemplate.send(topic, payload);
            log.info("{} sent to topic {}", payload, topic);
        } catch (Exception e) {
            log.error("Failed to send {} to topic {}", payload, topic, e.getMessage());
        }
    }
}
