// src/main/java/kasiKotas/service/SauceService.java
package kasiKotas.service;

import kasiKotas.model.Sauce;
import kasiKotas.repository.SauceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for managing Sauce related business logic.
 * This class handles creation, retrieval, updating, and deletion of sauces.
 * It includes basic validation for sauce details.
 */
@Service
@Transactional
public class SauceService {

    private final SauceRepository sauceRepository;

    @Autowired
    public SauceService(SauceRepository sauceRepository) {
        this.sauceRepository = sauceRepository;
    }

    /**
     * Retrieves all sauce items from the database.
     * @return A list of all Sauce objects.
     */
    public List<Sauce> getAllSauces() {
        return sauceRepository.findAll();
    }

    /**
     * Retrieves a sauce item by its ID.
     * @param id The ID of the sauce to retrieve.
     * @return An Optional containing the Sauce if found, or empty if not found.
     */
    public Optional<Sauce> getSauceById(Long id) {
        return sauceRepository.findById(id);
    }

    /**
     * Creates a new sauce item.
     * Includes basic validation logic.
     * @param sauce The Sauce object to save.
     * @return The saved Sauce object.
     * @throws IllegalArgumentException if sauce details are invalid.
     */
    public Sauce createSauce(Sauce sauce) {
        if (!StringUtils.hasText(sauce.getName())) {
            throw new IllegalArgumentException("Sauce name cannot be empty.");
        }
        // Price can be 0.0 as per requirement
        if (sauce.getPrice() == null || sauce.getPrice() < 0) {
            throw new IllegalArgumentException("Sauce price cannot be negative.");
        }
        // You could add a check for duplicate names if needed beyond unique constraint

        return sauceRepository.save(sauce);
    }

    /**
     * Updates an existing sauce item.
     * Includes basic validation logic.
     * @param id The ID of the sauce to update.
     * @param sauceDetails The updated Sauce object.
     * @return An Optional containing the updated Sauce if found, or empty if not found.
     * @throws IllegalArgumentException if sauce details are invalid.
     */
    public Optional<Sauce> updateSauce(Long id, Sauce sauceDetails) {
        return sauceRepository.findById(id)
                .map(existingSauce -> {
                    if (!StringUtils.hasText(sauceDetails.getName())) {
                        throw new IllegalArgumentException("Sauce name cannot be empty.");
                    }
                    if (sauceDetails.getPrice() == null || sauceDetails.getPrice() < 0) {
                        throw new IllegalArgumentException("Updated sauce price cannot be negative.");
                    }
                    // Handle unique name constraint if updating name to an existing one (excluding self)

                    existingSauce.setName(sauceDetails.getName());
                    existingSauce.setPrice(sauceDetails.getPrice());
                    existingSauce.setDescription(sauceDetails.getDescription());
                    return sauceRepository.save(existingSauce);
                });
    }

    /**
     * Deletes a sauce item by its ID.
     * @param id The ID of the sauce to delete.
     * @return true if the sauce was found and deleted, false otherwise.
     */
    public boolean deleteSauce(Long id) {
        if (sauceRepository.existsById(id)) {
            sauceRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
