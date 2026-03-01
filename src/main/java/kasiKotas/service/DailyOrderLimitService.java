// src/main/java/kasiKotas/service/DailyOrderLimitService.java
package kasiKotas.service;

import kasiKotas.model.DailyOrderLimit;
import kasiKotas.repository.DailyOrderLimitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

/**
 * Service layer for managing the Total Order Limit.
 * This service is responsible for retrieving and updating the single
 * total order limit setting.
 */
@Service
@Transactional // Ensures methods are transactional
public class DailyOrderLimitService {

    private final DailyOrderLimitRepository dailyOrderLimitRepository;

    @Autowired
    public DailyOrderLimitService(DailyOrderLimitRepository dailyOrderLimitRepository) {
        this.dailyOrderLimitRepository = dailyOrderLimitRepository;
    }

    /**
     * Retrieves the current total order limit.
     * Since we expect only one, it fetches the first one found.
     * @return An Optional containing the DailyOrderLimit (now TotalOrderLimit) if set, or empty.
     */
    public Optional<DailyOrderLimit> getOrderLimit() {
        // Fetch all and get the first, as we expect only one configuration record
        List<DailyOrderLimit> limits = dailyOrderLimitRepository.findAll();
        return limits.isEmpty() ? Optional.empty() : Optional.of(limits.get(0));
    }

    /**
     * Sets or updates the total order limit.
     * If a limit record already exists, it updates it. Otherwise, it creates a new one.
     * @param newLimitValue The new integer value for the total order limit.
     * @return The saved/updated DailyOrderLimit object.
     * @throws IllegalArgumentException if the new limit value is negative.
     */
    public DailyOrderLimit setOrderLimit(int newLimitValue) {
        if (newLimitValue < 0) {
            throw new IllegalArgumentException("Order limit cannot be negative.");
        }

        Optional<DailyOrderLimit> existingLimitOptional = getOrderLimit();
        DailyOrderLimit orderLimit;

        if (existingLimitOptional.isPresent()) {
            orderLimit = existingLimitOptional.get();
            orderLimit.setLimitValue(newLimitValue);
        } else {
            orderLimit = new DailyOrderLimit();
            orderLimit.setLimitValue(newLimitValue);
        }
        return dailyOrderLimitRepository.save(orderLimit);
    }

    /**
     * Decrements the order limit by a specified amount.
     * This is called when an order is placed to reduce the remaining capacity.
     * @param amount The amount to decrement (e.g., number of kotas ordered).
     * @return The updated DailyOrderLimit object.
     * @throws IllegalArgumentException if no limit exists or if decrement would make it negative.
     */
    public DailyOrderLimit decrementOrderLimit(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Decrement amount cannot be negative.");
        }

        Optional<DailyOrderLimit> limitOptional = getOrderLimit();
        if (limitOptional.isEmpty()) {
            throw new IllegalArgumentException("No order limit configured.");
        }

        DailyOrderLimit orderLimit = limitOptional.get();
        int newValue = orderLimit.getLimitValue() - amount;
        
        if (newValue < 0) {
            throw new IllegalArgumentException("Cannot decrement limit below 0. Current: " + 
                orderLimit.getLimitValue() + ", Requested decrement: " + amount);
        }

        orderLimit.setLimitValue(newValue);
        return dailyOrderLimitRepository.save(orderLimit);
    }

    /**
     * Increments the order limit by a specified amount.
     * This is called when an order is cancelled/deleted to restore capacity.
     * @param amount The amount to increment (e.g., number of kotas to restore).
     * @return The updated DailyOrderLimit object.
     */
    public DailyOrderLimit incrementOrderLimit(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Increment amount cannot be negative.");
        }

        Optional<DailyOrderLimit> limitOptional = getOrderLimit();
        if (limitOptional.isEmpty()) {
            throw new IllegalArgumentException("No order limit configured.");
        }

        DailyOrderLimit orderLimit = limitOptional.get();
        orderLimit.setLimitValue(orderLimit.getLimitValue() + amount);
        return dailyOrderLimitRepository.save(orderLimit);
    }
}
