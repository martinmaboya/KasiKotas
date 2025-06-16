// src/main/java/kasiKotas/service/ExtraService.java
package kasiKotas.service;

import kasiKotas.model.Extra;
import kasiKotas.repository.ExtraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for managing Extra (add-on) related business logic.
 * This class handles creation, retrieval, updating, and deletion of extras.
 * It includes basic validation for extra details.
 */
@Service
@Transactional
public class ExtraService {

    private final ExtraRepository extraRepository;

    @Autowired
    public ExtraService(ExtraRepository extraRepository) {
        this.extraRepository = extraRepository;
    }

    /**
     * Retrieves all extra items from the database.
     * @return A list of all Extra objects.
     */
    public List<Extra> getAllExtras() {
        return extraRepository.findAll();
    }

    /**
     * Retrieves an extra item by its ID.
     * @param id The ID of the extra to retrieve.
     * @return An Optional containing the Extra if found, or empty if not found.
     */
    public Optional<Extra> getExtraById(Long id) {
        return extraRepository.findById(id);
    }

    /**
     * Creates a new extra item.
     * Includes basic validation logic.
     * @param extra The Extra object to save.
     * @return The saved Extra object.
     * @throws IllegalArgumentException if extra details are invalid.
     */
    public Extra createExtra(Extra extra) {
        if (!StringUtils.hasText(extra.getName())) {
            throw new IllegalArgumentException("Extra name cannot be empty.");
        }
        if (extra.getPrice() == null || extra.getPrice() < 0) {
            throw new IllegalArgumentException("Extra price cannot be negative.");
        }
        // You could add a check for duplicate names if needed beyond unique constraint
        // if (extraRepository.findByName(extra.getName()).isPresent()) { ... }

        return extraRepository.save(extra);
    }

    /**
     * Updates an existing extra item.
     * Includes basic validation logic.
     * @param id The ID of the extra to update.
     * @param extraDetails The updated Extra object.
     * @return An Optional containing the updated Extra if found, or empty if not found.
     * @throws IllegalArgumentException if extra details are invalid.
     */
    public Optional<Extra> updateExtra(Long id, Extra extraDetails) {
        return extraRepository.findById(id)
                .map(existingExtra -> {
                    if (!StringUtils.hasText(extraDetails.getName())) {
                        throw new IllegalArgumentException("Extra name cannot be empty.");
                    }
                    if (extraDetails.getPrice() == null || extraDetails.getPrice() < 0) {
                        throw new IllegalArgumentException("Updated extra price cannot be negative.");
                    }
                    // Handle unique name constraint if updating name to an existing one (excluding self)

                    existingExtra.setName(extraDetails.getName());
                    existingExtra.setPrice(extraDetails.getPrice());
                    existingExtra.setDescription(extraDetails.getDescription());
                    return extraRepository.save(existingExtra);
                });
    }

    /**
     * Deletes an extra item by its ID.
     * @param id The ID of the extra to delete.
     * @return true if the extra was found and deleted, false otherwise.
     */
    public boolean deleteExtra(Long id) {
        if (extraRepository.existsById(id)) {
            extraRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
