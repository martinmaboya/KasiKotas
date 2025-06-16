// src/main/java/kasiKotas/controller/ExtraController.java
package kasiKotas.controller;

import kasiKotas.model.Extra;
import kasiKotas.service.ExtraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing Extra (add-on) items.
 * Exposes API endpoints for retrieving, adding, updating, and deleting extras.
 * These endpoints will primarily be used by the admin panel and the frontend
 * to fetch available extras.
 */
@RestController // Marks this class as a REST Controller, capable of handling HTTP requests.
@RequestMapping("/api/extras") // Defines the base URL path for all endpoints in this controller.
public class ExtraController {

    private final ExtraService extraService; // Injects the ExtraService dependency.

    @Autowired // Constructor injection is the recommended way for dependencies.
    public ExtraController(ExtraService extraService) {
        this.extraService = extraService;
    }

    /**
     * Retrieves a list of all extra items.
     * GET /api/extras
     * @return A ResponseEntity containing a list of Extra objects and HTTP status 200 OK.
     */
    @GetMapping // Maps HTTP GET requests to /api/extras
    public ResponseEntity<List<Extra>> getAllExtras() {
        List<Extra> extras = extraService.getAllExtras();
        return ResponseEntity.ok(extras); // Returns 200 OK with the list of extras.
    }

    /**
     * Retrieves a single extra item by its ID.
     * GET /api/extras/{id}
     * @param id The ID of the extra to retrieve.
     * @return A ResponseEntity containing the Extra if found (200 OK), or 404 Not Found.
     */
    @GetMapping("/{id}") // Maps HTTP GET requests to /api/extras/{id}
    public ResponseEntity<Extra> getExtraById(@PathVariable Long id) {
        return extraService.getExtraById(id)
                .map(ResponseEntity::ok) // If extra found, return 200 OK with the extra.
                .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found.
    }

    /**
     * Creates a new extra item.
     * POST /api/extras
     * @param extra The Extra object to create (sent in the request body).
     * @return A ResponseEntity containing the created Extra and 201 Created status,
     * or 400 Bad Request if validation fails.
     */
    @PostMapping // Maps HTTP POST requests to /api/extras
    public ResponseEntity<Extra> createExtra(@RequestBody Extra extra) {
        try {
            Extra createdExtra = extraService.createExtra(extra);
            return new ResponseEntity<>(createdExtra, HttpStatus.CREATED); // Returns 201 Created on success.
        } catch (IllegalArgumentException e) {
            // Catches validation errors from the service layer, returning 400 Bad Request.
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Updates an existing extra item.
     * PUT /api/extras/{id}
     * @param id The ID of the extra to update.
     * @param extraDetails The updated Extra object (sent in the request body).
     * @return A ResponseEntity containing the updated Extra (200 OK), 404 Not Found,
     * or 400 Bad Request if validation fails.
     */
    @PutMapping("/{id}") // Maps HTTP PUT requests to /api/extras/{id}
    public ResponseEntity<Extra> updateExtra(@PathVariable Long id, @RequestBody Extra extraDetails) {
        try {
            return extraService.updateExtra(id, extraDetails)
                    .map(ResponseEntity::ok) // If extra updated, return 200 OK.
                    .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found.
        } catch (IllegalArgumentException e) {
            // Catches validation errors from the service layer, returning 400 Bad Request.
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deletes an extra item by its ID.
     * DELETE /api/extras/{id}
     * @param id The ID of the extra to delete.
     * @return A ResponseEntity with 204 No Content if successful, or 404 Not Found.
     */
    @DeleteMapping("/{id}") // Maps HTTP DELETE requests to /api/extras/{id}
    public ResponseEntity<Void> deleteExtra(@PathVariable Long id) {
        boolean deleted = extraService.deleteExtra(id);
        if (deleted) {
            return ResponseEntity.noContent().build(); // Returns 204 No Content on successful deletion.
        } else {
            return ResponseEntity.notFound().build(); // Returns 404 Not Found if extra not found.
        }
    }
}
