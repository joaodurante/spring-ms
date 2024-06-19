package br.com.microservices.orchestrated.inventoryservice.core.service;

import br.com.microservices.orchestrated.inventoryservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Event;
import br.com.microservices.orchestrated.inventoryservice.core.dto.History;
import br.com.microservices.orchestrated.inventoryservice.core.dto.Order;
import br.com.microservices.orchestrated.inventoryservice.core.dto.OrderProduct;
import br.com.microservices.orchestrated.inventoryservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.inventoryservice.core.model.Inventory;
import br.com.microservices.orchestrated.inventoryservice.core.model.OrderInventory;
import br.com.microservices.orchestrated.inventoryservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.inventoryservice.core.repository.InventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.repository.OrderInventoryRepository;
import br.com.microservices.orchestrated.inventoryservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class InventoryService {
    private static final String CURRENT_SOURCE = "INVENTORY_SERVICE";
    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;
    private final InventoryRepository inventoryRepository;
    private final OrderInventoryRepository orderInventoryRepository;

    public void updateInventory(Event event) {
        try {
            checkCurrentOrderInventory(event);
            saveOrderInventory(event);
            updateInventory(event.getPayload());
            handleSuccess(event);
        } catch(Exception e) {
            log.error("Error trying to update inventory: ", e);
            handleFail(event, e.getMessage());
        }
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void checkCurrentOrderInventory(Event event) {
        if(orderInventoryRepository.existsByOrderIdAndTransactionId(
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

    private void saveOrderInventory(Event event) {
        event.getPayload().getProducts().forEach(product -> {
            var inventory = findInventoryByProductCode(product.getProduct().getCode());
            var orderInventory = createOrderInventoryObject(event, product, inventory);
            orderInventoryRepository.save(orderInventory);
        });
    }

    private OrderInventory createOrderInventoryObject(Event event, OrderProduct orderProduct, Inventory inventory) {
        return OrderInventory
                .builder()
                .inventory(inventory)
                .orderId(event.getOrderId())
                .transactionId(event.getTransactionId())
                .oldQuantityAvailable(inventory.getAvailableQuantity())
                .orderQuantity(orderProduct.getQuantity())
                .newQuantityAvailable(inventory.getAvailableQuantity() - orderProduct.getQuantity())
                .build();
    }

    private void updateInventory(Order order) {
        order.getProducts().forEach(product -> {
            var inventory = findInventoryByProductCode(product.getProduct().getCode());
            validateQuantities(inventory.getAvailableQuantity(), product.getQuantity());
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - product.getQuantity());
            inventoryRepository.save(inventory);
        });
    }

    private void validateQuantities(Integer availableQuantity, Integer orderQuantity) {
        if(availableQuantity < orderQuantity) {
            throw new ValidationException("Product is out of stock.");
        }
    }

    private Inventory findInventoryByProductCode(String productCode) {
        return inventoryRepository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ValidationException("Failed to find inventory using the informed productCode"));
    }

    private void handleSuccess(Event event) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.SUCCESS);
        addToHistory(event, "Inventory updated successfully.");
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

    private void handleFail(Event event, String message) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        addToHistory(event, String.format("Fail to realize update inventory. %s", message));
    }

    private void updateInventoryToPreviousValues(Event event) {
        orderInventoryRepository
                .findByOrderIdAndTransactionId(
                    event.getOrderId(),
                    event.getTransactionId())
                .forEach(orderInventory -> {
                    var inventory = orderInventory.getInventory();
                    inventory.setAvailableQuantity(orderInventory.getOldQuantityAvailable());
                    inventoryRepository.save(inventory);
                    log.info("Restored inventory for order {} from {} to {}",
                            event.getOrderId(),
                            orderInventory.getNewQuantityAvailable(),
                            inventory.getAvailableQuantity()
                    );
                });
    }

    public void rollbackInventory(Event event) {
        event.setSource(CURRENT_SOURCE);
        event.setStatus(ESagaStatus.FAIL);

        try {
            updateInventoryToPreviousValues(event);
            addToHistory(event, "Rollback executed for inventory");
        } catch (Exception e) {
            addToHistory(event, "Rollback not executed for inventory: ".concat(e.getMessage()));
        }
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }
}
