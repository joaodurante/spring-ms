package br.com.microservices.orchestrated.inventoryservice.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String id;
    private List<OrderProduct> products;
    private double totalAmount;
    private int totalItems;
    private LocalDateTime createdAt;
    private String transactionId;
}
