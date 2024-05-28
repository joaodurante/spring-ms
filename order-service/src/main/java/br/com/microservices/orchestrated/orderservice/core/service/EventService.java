package br.com.microservices.orchestrated.orderservice.core.service;

import br.com.microservices.orchestrated.orderservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.dto.EventFilters;
import br.com.microservices.orchestrated.orderservice.core.repository.EventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class EventService {
    private final EventRepository repository;

    public List<Event> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Event findByFilters(EventFilters filters) {
        validateEmptyFilters(filters);
        if(!isEmpty(filters.getOrderId())) {
            return repository
                    .findTop1ByOrderIdOrderByCreatedAtDesc(filters.getOrderId())
                    .orElseThrow(() -> new ValidationException("Event not found by OrderID."));
        } else {
            return repository
                    .findTop1ByTransactionIdOrderByCreatedAtDesc(filters.getTransactionId())
                    .orElseThrow(() -> new ValidationException("Event not found by TransactionID."));
        }
    }

    private void validateEmptyFilters(EventFilters filters) {
        if(isEmpty(filters.getOrderId()) && isEmpty(filters.getTransactionId())) {
            throw new ValidationException("OrderID or TransactionID must be informed.");
        }
    }

    public Event save(Event event) {
        return repository.save(event);
    }

    public void notifyEnding(Event event) {
        event.setOrderId(event.getOrderId());
        event.setCreatedAt(LocalDateTime.now());
        save(event);
        log.info("Order {} with saga notified! TransactionId: {}", event.getOrderId(), event.getTransactionId());
    }
}
