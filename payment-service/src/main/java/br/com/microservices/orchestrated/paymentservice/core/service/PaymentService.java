package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {
    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static final Double MINIMUN_AMOUNT_VALUE = 0.1;
    private static final Double REDUCE_SUM_VALUE = 0.0;

    private final JsonUtil jsonUtil;
    private final PaymentRepository paymentRepository;
    private final KafkaProducer producer;

    public void realizePayment(Event event) {
        try {
            validateEvent(event);
            checkCurrentPayment(event);
            createPayment(event);
            var payment = findPaymentByOrderIdAndTransactionId(event);
            validateTotalAmount(payment.getTotalAmount());
            updatePaymentStatus(event, EPaymentStatus.SUCCESS);
            handleSuccess(event);
        } catch(Exception e) {
            log.error("Error trying to realize payment: ", e);
            handleFail(event, e.getMessage());
        }

        producer.sendEvent(jsonUtil.toJson(event));
    }

    public void realizeRefund(Event event) {
        try {
            updatePaymentStatus(event, EPaymentStatus.REFUND);
            handleRollback(event, "Refund realized successfully.");
        } catch(Exception e) {
            handleRollback(event, "Failed to realize refund. ".concat(e.getMessage()));
        }
    }

    private void validateEvent(Event event) {
        if(isEmpty(event.getOrderId()) || isEmpty(event.getTransactionId())) {
            throw new ValidationException("OrderID and TransactionID should not be empty");
        }
    }

    private void checkCurrentPayment(Event event) {
        if(paymentRepository.existsByOrderIdAndTransactionId(
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

    private void savePayment(Payment payment) {
        paymentRepository.save(payment);
    }

    private void createPayment(Event event) {
        var payment = Payment
                .builder()
                .orderId(event.getOrderId())
                .transactionId(event.getTransactionId())
                .totalItems(calculateTotalItems(event))
                .totalAmount(calculateTotalAmount(event))
                .build();
        savePayment(payment);
        updateEventTotal(event, payment);
    }

    private Integer calculateTotalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProduct::getQuantity)
                .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private Double calculateTotalAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(orderProduct -> orderProduct.getQuantity() * orderProduct.getProduct().getUnitValue())
                .reduce(REDUCE_SUM_VALUE, Double::sum);
    }

    private void updateEventTotal(Event event, Payment payment) {
        event.getPayload().setTotalItems(payment.getTotalItems());
        event.getPayload().setTotalAmount(payment.getTotalAmount());
    }

    private Payment findPaymentByOrderIdAndTransactionId(Event event) {
        return paymentRepository.findByOrderIdAndTransactionId(
                event.getOrderId(),
                event.getTransactionId()
        ).orElseThrow(() -> new ValidationException("Payment not found"));
    }

    private void validateTotalAmount(Double amount) {
        if(amount < MINIMUN_AMOUNT_VALUE) {
            throw new ValidationException("Total amount must be greater than ".concat(MINIMUN_AMOUNT_VALUE.toString()));
        }
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

    private void updatePaymentStatus(Event event, EPaymentStatus status) {
        var payment = findPaymentByOrderIdAndTransactionId(event);
        payment.setStatus(status);
        savePayment(payment);
    }

    private void handleSuccess(Event event) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.SUCCESS);
        addToHistory(event, "Payment realized successfully.");
    }

    private void handleFail(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        addToHistory(event, String.format("Fail to realize payment. %s", message));
    }

    private void handleRollback(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.FAIL);
        addToHistory(event, message);
        producer.sendEvent(jsonUtil.toJson(event));
    }
}
