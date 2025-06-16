// src/main/java/kasiKotas/controller/UserController.java
package kasiKotas.controller;

import kasiKotas.model.User;
import kasiKotas.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // For login request body

/**
 * REST Controller for managing User related operations.
 * Exposes API endpoints for user registration, login, and user management.
 *
 * IMPORTANT SECURITY NOTE:
 * This controller currently handles passwords in plain text for simplicity.
 * IN A REAL PRODUCTION APPLICATION, YOU MUST:
 * 1. Hash passwords (e.g., using BCryptPasswordEncoder) before storing them.
 * 2. Implement robust authentication (e.g., Spring Security with JWT or Sessions)
 * for login and securing endpoints based on user roles.
 */
@RestController
@RequestMapping("/api/users") // Base path for user-related endpoints
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registers a new user.
     * POST /api/users/register
     * @param user The User object to register (sent in the request body).
     * @return A ResponseEntity with the created User (201 Created) or 400 Bad Request on validation failure.
     */
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        try {
            User registeredUser = userService.registerUser(user);
            return new ResponseEntity<>(registeredUser, HttpStatus.CREATED); // 201 Created
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // 400 Bad Request for validation errors
        }
    }

    /**
     * Authenticates a user (login).
     * POST /api/users/login
     * @param loginRequest A map containing "email" and "password".
     * @return A ResponseEntity with the authenticated User (200 OK) or 401 Unauthorized.
     */
    @PostMapping("/login")
    public ResponseEntity<User> loginUser(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        return userService.authenticateUser(email, password)
                .map(ResponseEntity::ok) // 200 OK if authenticated
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()); // 401 Unauthorized
    }

    /**
     * Retrieves all users (typically for admin use).
     * GET /api/users
     * @return A list of all User objects (200 OK).
     */
    @GetMapping // In a real app, this would be secured (e.g., only for ADMIN role)
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Retrieves a single user by ID.
     * GET /api/users/{id}
     * @param id The ID of the user to retrieve.
     * @return The User object (200 OK) or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Updates an existing user's information.
     * PUT /api/users/{id}
     * @param id The ID of the user to update.
     * @param userDetails The updated User object.
     * @return The updated User object (200 OK), 404 Not Found, or 400 Bad Request.
     */
    @PutMapping("/{id}") // In a real app, users can only update their own profile unless ADMIN
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        try {
            return userService.updateUser(id, userDetails)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Updates a user's password.
     * PUT /api/users/{id}/password
     * @param id The ID of the user.
     * @param requestBody A map containing "newPassword".
     * @return 200 OK if password updated, 404 Not Found if user not found, 400 Bad Request if invalid password.
     */
    @PutMapping("/{id}/password") // This would also be secured
    public ResponseEntity<Void> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        String newPassword = requestBody.get("newPassword");
        try {
            boolean updated = userService.updatePassword(id, newPassword);
            if (updated) {
                return ResponseEntity.ok().build(); // 200 OK
            } else {
                return ResponseEntity.notFound().build(); // 404 Not Found (user not found)
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // 400 Bad Request (invalid password)
        }
    }

    /**
     * Deletes a user by their ID.
     * DELETE /api/users/{id}
     * @param id The ID of the user to delete.
     * @return 204 No Content if successful, or 404 Not Found.
     */
    @DeleteMapping("/{id}") // In a real app, only ADMIN can delete users
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        if (deleted) {
            return ResponseEntity.noContent().build(); // 204 No Content
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }
}
