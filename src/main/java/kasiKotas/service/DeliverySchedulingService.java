package kasiKotas.service;

import kasiKotas.model.Order;
import kasiKotas.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class DeliverySchedulingService {

    private static final Logger log = LoggerFactory.getLogger(DeliverySchedulingService.class);

    private final OrderRepository orderRepository;

    @Autowired
    public DeliverySchedulingService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Processes scheduled orders (both delivery and collection) that are due within the next 30 minutes.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    public void processScheduledDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime window = now.plusMinutes(30);

        List<Order> ordersToProcess = orderRepository.findByScheduledDeliveryTimeBetweenAndStatus(
                now, window, Order.OrderStatus.PENDING
        );

        for (Order order : ordersToProcess) {
            try {
                order.setStatus(Order.OrderStatus.PROCESSING);
                orderRepository.save(order);
                log.info("Processing scheduled {} order #{} due at {}",
                        order.getDeliveryMethod(), order.getId(), order.getScheduledDeliveryTime());
            } catch (Exception e) {
                log.error("Error processing scheduled order #{}: {}", order.getId(), e.getMessage());
            }
        }

        if (!ordersToProcess.isEmpty()) {
            log.info("Processed {} scheduled orders", ordersToProcess.size());
        }
    }

    public List<Order> getAllScheduledOrders() {
        return orderRepository.findByScheduledDeliveryTimeIsNotNull();
    }

    public List<Order> getOrdersInTimeRange(LocalDateTime start, LocalDateTime end, Order.OrderStatus status) {
        return orderRepository.findByScheduledDeliveryTimeBetweenAndStatus(start, end, status);
    }
}
