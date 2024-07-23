package br.com.microservices.orchestrated.orchestratorservice.core.service;

import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.History;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.orchestratorservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaExecutionController;
import br.com.microservices.orchestrated.orchestratorservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource.ORCHESTRATOR;
import static br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics.NOTIFY_ENDING;

@Slf4j
@Service
@AllArgsConstructor
public class OrchestratorService {
    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final SagaExecutionController sagaExecutionController;

    public void startSaga(Event event) {
        event.setSource(ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        var nextTopic = sagaExecutionController.getNextTopic(event);
        log.info("SAGA STARTED");
        addToHistory(event, "Saga started.");
        kafkaProducer.sendEvent(nextTopic.getTopic(), jsonUtil.toJson(event));
    }

    public void continueSaga(Event event) {
        var nextTopic = sagaExecutionController.getNextTopic(event);
        log.info("SAGA CONTINUING FOR EVENT: {}", event.getId());
        kafkaProducer.sendEvent(nextTopic.getTopic(), jsonUtil.toJson(event));
    }

    public void finishSagaSuccess(Event event) {
        event.setSource(ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        log.info("SAGA FINISHED SUCCESSFULLY FOR EVENT: {}", event.getId());
        addToHistory(event, "Saga finished successfully.");
        kafkaProducer.sendEvent(NOTIFY_ENDING.getTopic(), jsonUtil.toJson(event));
    }

    public void finishSagaFail(Event event) {
        event.setSource(ORCHESTRATOR);
        event.setStatus(ESagaStatus.FAIL);
        log.info("SAGA FINISHED UNSUCCESSFULLY FOR EVENT: {}", event.getId());
        addToHistory(event, "Saga finished unsuccessfully.");
        kafkaProducer.sendEvent(NOTIFY_ENDING.getTopic(), jsonUtil.toJson(event));
    }

    private void addToHistory(Event event, String message) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToEventHistory(history);
    }
}
