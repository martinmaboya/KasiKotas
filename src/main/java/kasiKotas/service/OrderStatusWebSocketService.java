
package kasiKotas.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderStatusWebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public OrderStatusWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendOrderStatusUpdate(Long orderId, String status, Long userId) {
        // You can customize the payload as needed
        var payload = new java.util.HashMap<String, Object>();
        payload.put("orderId", orderId);
        payload.put("status", status);
        payload.put("userId", userId);
        messagingTemplate.convertAndSend("/topic/orderStatusUpdate", payload);
    }
}
