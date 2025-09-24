package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing scheduled deliveries.
 * This service runs scheduled tasks to process orders that are due for delivery.
 */
@Service
@Transactional
public class DeliverySchedulingService {

    private final OrderRepository orderRepository;
    // private final EmailService emailService;
    
    @Autowired
    public DeliverySchedulingService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        // this.emailService = emailService;
    }
    
    /**
     * Processes scheduled deliveries by checking for orders that should be started within the next 30 minutes.
     * Runs every 5 minutes to ensure timely processing.
     */
    @Scheduled(fixedRate = 300000) // Check every 5 minutes (300000 ms)
    public void processScheduledDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deliveryWindow = now.plusMinutes(30); // 30-minute window
        
        List<Order> ordersToProcess = orderRepository.findByScheduledDeliveryTimeBetweenAndStatus(
            now, deliveryWindow, Order.OrderStatus.PENDING
        );
        
        for (Order order : ordersToProcess) {
            try {
                // Update status to PROCESSING
                order.setStatus(Order.OrderStatus.PROCESSING);
                orderRepository.save(order);
                
                System.out.println("Started processing scheduled order #" + order.getId() + 
                                 " for delivery at " + order.getScheduledDeliveryTime());
                
                // Optional: Send notification to kitchen/delivery team
                // You can implement this based on your notification requirements
                // notifyKitchen(order);
                
            } catch (Exception e) {
                System.err.println("Error processing scheduled order #" + order.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (!ordersToProcess.isEmpty()) {
            System.out.println("Processed " + ordersToProcess.size() + " scheduled orders");
        }
    }
    
    /**
     * Gets all orders that have scheduled delivery times (not immediate orders).
     * @return List of orders with scheduled delivery times
     */
    public List<Order> getAllScheduledOrders() {
        return orderRepository.findByScheduledDeliveryTimeIsNotNull();
    }
    
    /**
     * Gets orders scheduled for a specific time range.
     * @param start Start of the time range
     * @param end End of the time range
     * @param status Status of orders to filter by
     * @return List of orders in the specified time range and status
     */
    public List<Order> getOrdersInTimeRange(LocalDateTime start, LocalDateTime end, Order.OrderStatus status) {
        return orderRepository.findByScheduledDeliveryTimeBetweenAndStatus(start, end, status);
    }
}
