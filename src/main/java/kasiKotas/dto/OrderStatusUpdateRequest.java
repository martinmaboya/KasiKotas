// src/main/java/kasiKotas/dto/OrderStatusUpdateRequest.java
package kasiKotas.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) for updating an order's status.
 * This class is used to map the JSON request body sent from the frontend
 * for the /api/orders/{orderId}/status endpoint.
 *
 * It provides a clear structure for the expected payload, making JSON deserialization
 * more robust and less prone to misinterpretations by Jackson.
 */
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode methods
@NoArgsConstructor // Lombok: Generates a no-argument constructor
@AllArgsConstructor // Lombok: Generates a constructor with all fields
public class OrderStatusUpdateRequest {
    // This field will directly map to the "status" key in the incoming JSON (e.g., {"status": "PROCESSING"})
    private String status;
}
