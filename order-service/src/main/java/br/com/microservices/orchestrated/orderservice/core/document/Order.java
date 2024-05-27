package br.com.microservices.orchestrated.orderservice.core.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collation = "order")
public class Order {
   @Id
   private String id;
   private List<OrderProduct> products;
   private double totalAmount;
   private int totalItems;
   private LocalDateTime createdAt;
   private String transactionId;
}
