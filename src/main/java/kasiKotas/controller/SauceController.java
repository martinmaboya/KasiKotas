// src/main/java/kasiKotas/controller/SauceController.java
package kasiKotas.controller;

import kasiKotas.model.Sauce;
import kasiKotas.service.SauceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing Sauce items.
 * Exposes API endpoints for retrieving, adding, updating, and deleting sauces.
 * These endpoints will primarily be used by the admin panel and the frontend
 * to fetch available sauces.
 */
@RestController // Marks this class as a REST Controller, capable of handling HTTP requests.
@RequestMapping("/api/sauces") // Defines the base URL path for all endpoints in this controller.
public class SauceController {

    private final SauceService sauceService; // Injects the SauceService dependency.

    @Autowired // Constructor injection is the recommended way for dependencies.
    public SauceController(SauceService sauceService) {
        this.sauceService = sauceService;
    }

    /**
     * Retrieves a list of all sauce items.
     * GET /api/sauces
     * @return A ResponseEntity containing a list of Sauce objects and HTTP status 200 OK.
     */
    @GetMapping // Maps HTTP GET requests to /api/sauces
    public ResponseEntity<List<Sauce>> getAllSauces() {
        List<Sauce> sauces = sauceService.getAllSauces();
        return ResponseEntity.ok(sauces); // Returns 200 OK with the list of sauces.
    }

    /**
     * Retrieves a single sauce item by its ID.
     * GET /api/sauces/{id}
     * @param id The ID of the sauce to retrieve.
     * @return A ResponseEntity containing the Sauce if found (200 OK), or 404 Not Found.
     */
    @GetMapping("/{id}") // Maps HTTP GET requests to /api/sauces/{id}
    public ResponseEntity<Sauce> getSauceById(@PathVariable Long id) {
        return sauceService.getSauceById(id)
                .map(ResponseEntity::ok) // If sauce found, return 200 OK with the sauce.
                .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found.
    }

    /**
     * Creates a new sauce item.
     * POST /api/sauces
     * @param sauce The Sauce object to create (sent in the request body).
     * @return A ResponseEntity containing the created Sauce and 201 Created status,
     * or 400 Bad Request if validation fails.
     *
     * IMPORTANT: This endpoint needs proper authentication and authorization (ADMIN role).
     */
    @PostMapping // Maps HTTP POST requests to /api/sauces
    public ResponseEntity<Sauce> createSauce(@RequestBody Sauce sauce) {
        try {
            Sauce createdSauce = sauceService.createSauce(sauce);
            return new ResponseEntity<>(createdSauce, HttpStatus.CREATED); // Returns 201 Created on success.
        } catch (IllegalArgumentException e) {
            // Catches validation errors from the service layer, returning 400 Bad Request.
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Updates an existing sauce item.
     * PUT /api/sauces/{id}
     * @param id The ID of the sauce to update.
     * @param sauceDetails The updated Sauce object (sent in the request body).
     * @return A ResponseEntity containing the updated Sauce (200 OK), 404 Not Found,
     * or 400 Bad Request if validation fails.
     *
     * IMPORTANT: This endpoint needs proper authentication and authorization (ADMIN role).
     */
    @PutMapping("/{id}") // Maps HTTP PUT requests to /api/sauces/{id}
    public ResponseEntity<Sauce> updateSauce(@PathVariable Long id, @RequestBody Sauce sauceDetails) {
        try {
            return sauceService.updateSauce(id, sauceDetails)
                    .map(ResponseEntity::ok) // If sauce updated, return 200 OK.
                    .orElseGet(() -> ResponseEntity.notFound().build()); // If not found, return 404 Not Found.
        } catch (IllegalArgumentException e) {
            // Catches validation errors from the service layer, returning 400 Bad Request.
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deletes a sauce item by its ID.
     * DELETE /api/sauces/{id}
     * @param id The ID of the sauce to delete.
     * @return A ResponseEntity with 204 No Content if successful, or 404 Not Found.
     *
     * IMPORTANT: This endpoint needs proper authentication and authorization (ADMIN role).
     */
    @DeleteMapping("/{id}") // Maps HTTP DELETE requests to /api/sauces/{id}
    public ResponseEntity<Void> deleteSauce(@PathVariable Long id) {
        boolean deleted = sauceService.deleteSauce(id);
        if (deleted) {
            return ResponseEntity.noContent().build(); // Returns 204 No Content on successful deletion.
        } else {
            return ResponseEntity.notFound().build(); // Returns 404 Not Found if sauce not found.
        }
    }
}
