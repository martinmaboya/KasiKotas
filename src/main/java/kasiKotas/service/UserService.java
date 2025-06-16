// src/main/java/kasiKotas/service/UserService.java
package kasiKotas.service;

import kasiKotas.model.User;
import kasiKotas.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // For String utility methods

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern; // For email validation

/**
 * Service layer for managing User related business logic.
 * This includes operations for user registration, retrieval, updates, etc.
 */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    // Basic email pattern validation (can be more comprehensive)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user with validation.
     * IMPORTANT: In a real application, the password MUST be encrypted (e.g., using BCryptPasswordEncoder)
     * before saving and never stored in plain text. This example saves it as-is for simplicity.
     *
     * @param user The User object to register.
     * @return The saved User object.
     * @throws IllegalArgumentException if user details are invalid or email already exists.
     */
    public User registerUser(User user) {
        // --- Validation for user registration ---
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Email cannot be empty.");
        }
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists.");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        if (user.getPassword().length() < 6) { // Example: Minimum password length
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        if (!StringUtils.hasText(user.getFirstName())) {
            throw new IllegalArgumentException("First name cannot be empty.");
        }
        if (!StringUtils.hasText(user.getLastName())) {
            throw new IllegalArgumentException("Last name cannot be empty.");
        }
        if (user.getRole() == null) {
            // Default role if not provided, or throw error depending on business rule
            user.setRole(User.UserRole.CUSTOMER);
        }
        // ----------------------------------------

        // In a real application, hash the password here:
        // user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    /**
     * Authenticates a user.
     * IMPORTANT: In a real application, you'd compare the provided plain text password
     * with the HASHED password stored in the database.
     *
     * @param email The user's email.
     * @param password The user's plain text password.
     * @return An Optional containing the User if credentials are valid, or empty otherwise.
     */
    public Optional<User> authenticateUser(String email, String Stringpassword) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(Stringpassword)) {
            return Optional.empty(); // No empty email or password
        }

        return userRepository.findByEmail(email)
                .filter(user -> {
                    // In a real app: passwordEncoder.matches(Stringpassword, user.getPassword());
                    return user.getPassword().equals(Stringpassword); // Simple comparison for now
                });
    }

    /**
     * Retrieves all users.
     * @return A list of all User objects.
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Retrieves a user by their ID.
     * @param id The ID of the user to retrieve.
     * @return An Optional containing the User if found, or empty if not found.
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Updates an existing user's information with validation.
     * Password updates should ideally be handled by a separate, dedicated method for security.
     * @param id The ID of the user to update.
     * @param userDetails The updated User object.
     * @return An Optional containing the updated User if found, or empty if not found.
     * @throws IllegalArgumentException if user details are invalid.
     */
    public Optional<User> updateUser(Long id, User userDetails) {
        return userRepository.findById(id)
                .map(existingUser -> {
                    // --- Validation for user update ---
                    if (!StringUtils.hasText(userDetails.getFirstName())) {
                        throw new IllegalArgumentException("First name cannot be empty.");
                    }
                    if (!StringUtils.hasText(userDetails.getLastName())) {
                        throw new IllegalArgumentException("Last name cannot be empty.");
                    }
                    // Email change logic would be more complex (e.g., check uniqueness)
                    // For now, if email is provided and different, validate its format
                    if (StringUtils.hasText(userDetails.getEmail()) && !userDetails.getEmail().equals(existingUser.getEmail())) {
                        if (!EMAIL_PATTERN.matcher(userDetails.getEmail()).matches()) {
                            throw new IllegalArgumentException("Invalid email format for update.");
                        }
                        if (userRepository.findByEmail(userDetails.getEmail()).isPresent()) {
                            throw new IllegalArgumentException("New email already in use by another user.");
                        }
                        existingUser.setEmail(userDetails.getEmail());
                    }
                    // Role update might require admin privileges in a real app
                    if (userDetails.getRole() != null) {
                        existingUser.setRole(userDetails.getRole());
                    }
                    // ------------------------------------

                    existingUser.setFirstName(userDetails.getFirstName());
                    existingUser.setLastName(userDetails.getLastName());
                    existingUser.setRoomNumber(userDetails.getRoomNumber());
                    existingUser.setPhoneNumber(userDetails.getPhoneNumber());

                    return userRepository.save(existingUser);
                });
    }

    /**
     * Updates a user's password securely.
     * In a real application, this would involve hashing the new password.
     * @param userId The ID of the user.
     * @param newPassword The new password (plain text).
     * @return true if password updated, false if user not found or password invalid.
     * @throws IllegalArgumentException if new password is empty or too short.
     */
    public boolean updatePassword(Long userId, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must not be empty and be at least 6 characters long.");
        }
        return userRepository.findById(userId)
                .map(user -> {
                    // In a real app: user.setPassword(passwordEncoder.encode(newPassword));
                    user.setPassword(newPassword); // Saving plain text for now
                    userRepository.save(user);
                    return true;
                }).orElse(false);
    }

    /**
     * Deletes a user by their ID.
     * @param id The ID of the user to delete.
     * @return true if the user was found and deleted, false otherwise.
     */
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
