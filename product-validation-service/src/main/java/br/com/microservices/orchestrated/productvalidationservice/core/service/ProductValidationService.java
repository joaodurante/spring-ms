package br.com.microservices.orchestrated.productvalidationservice.core.service;

import br.com.microservices.orchestrated.productvalidationservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.Event;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.History;
import br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.productvalidationservice.core.model.Validation;
import br.com.microservices.orchestrated.productvalidationservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductValidationService {
    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";
    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final ProductRepository productRepository;
    private final ValidationRepository validationRepository;

    public void validate(Event event) {
        try {
            validateEvent(event);
            checkCurrentValidation(event);
            validateProductList(event);
            handleSuccess(event);
        } catch(Exception e) {
            log.error("Error trying to validate products: ", e);
            handleFail(event, e.getMessage());
        }

        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void checkCurrentValidation(Event event) {
        if(validationRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(),
                event.getTransactionId()
        )) {
            throw new ValidationException(String.format(
                    "There's another transaction for this validation. OrderID: %s - TransactionID: %s",
                    event.getOrderId(),
                    event.getTransactionId()
            ));
        }
    }

    private void validateEvent(Event event) {
        if(isEmpty(event.getOrderId()) || isEmpty(event.getTransactionId())) {
            throw new ValidationException("OrderID and TransactionID should not be empty");
        }

        if(isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts())) {
            throw new ValidationException(String.format("The product list is empty.", event.getOrderId()));
        }
    }

    private void validateProductList(Event event) {
        event.getPayload().getProducts().forEach(product -> {
            if(isEmpty(product) || isEmpty(product.getProduct().getCode())) {
                throw new ValidationException(String.format("Invalid product.", event.getOrderId()));
            }

            if(!productRepository.existsByCode(product.getProduct().getCode())) {
                throw new ValidationException(String.format("The product %s does not exists.", product.getProduct().getCode()));
            }
        });
    }

    private void createValidation(Event event, boolean success) {
        var validation = Validation
                .builder()
                .orderId(event.getOrderId())
                .transactionId(event.getTransactionId())
                .success(success)
                .build();
        validationRepository.save(validation);
    }

    private void addToHistory(Event event, String message) {
        var history = History
                .builder()
                .source(CURRENT_SOURCE)
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToEventHistory(history);
    }

    private void handleSuccess(Event event) {
        createValidation(event, true);
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addToHistory(event, "Products validated successfully.");
    }

    private void handleFail(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        addToHistory(event, String.format("Fail to validate product. %s", message));
    }

    public void rollbackEvent(Event event) {
        updateValidationToFail(event);
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.FAIL);
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void updateValidationToFail(Event event) {
        validationRepository
                .findByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())
                .ifPresentOrElse(
                        validation -> {
                            validation.setSuccess(false);
                            validationRepository.save(validation);
                        },
                        () -> createValidation(event, false)
                );
    }
}
