package br.com.microservices.orchestrated.orchestratorservice.core.saga;

import br.com.microservices.orchestrated.orchestratorservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaHandler.*;
import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Component
@AllArgsConstructor
public class SagaExecutionController {
    public ETopics getNextTopic(Event event) {
        if(isEmpty(event.getSource()) || isEmpty(event.getStatus())) {
            throw new ValidationException("Source and Status must be informed!");
        }

        var topic = findTopicBySourceAndStatus(event);
        logCurrentSaga(event, topic);
        return topic;
    }

    private ETopics findTopicBySourceAndStatus(Event event) {
        return (ETopics) (Arrays.stream(SAGA_HANDLER)
                .filter(row -> filterEventBySourceAndStatus(event, row))
                .map(row -> row[TOPIC_INDEX])
                .findFirst()
                .orElseThrow(() -> new ValidationException("Topic not found")));
    }

    private boolean filterEventBySourceAndStatus(Event event, Object[] row) {
        var source = row[EVENT_SOURCE_INDEX];
        var status = row[SAGA_STATUS_INDEX];
        return event.getSource().equals(source) && event.getStatus().equals(status);
    }

    private void logCurrentSaga(Event event, ETopics topic) {
        var sagaId = getSagaId(event);

        switch(event.getStatus()) {
            case SUCCESS -> log.info("### CURRENT_SAGA: {} | SUCCESS | NEXT_TOPIC: {} | {}", event.getSource(), topic, sagaId);
            case ROLLBACK_PENDING -> log.info("### CURRENT_SAGA: {} | ROLLBACK (CURRENT SERVICE) | NEXT_TOPIC: {} | {}",
                    event.getSource(), topic, sagaId);
            case FAIL -> log.info("### CURRENT_SAGA: {} | ROLLBACK (PREVIOUS SERVICE) | NEXT_TOPIC: {} | {}",
                    event.getSource(), topic, getSagaId(event));
        }
    }

    private String getSagaId(Event event) {
        return String.format("ORDER_ID: %s | TRANSACTION_ID: %s | EVENT_ID: %s",
                event.getPayload().getId(), event.getTransactionId(), event.getId());
    }
}
