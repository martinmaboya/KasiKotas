// src/main/java/kasiKotas/service/UserService.java
    package kasiKotas.service;

    import kasiKotas.model.User;
    import kasiKotas.repository.UserRepository;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.util.StringUtils;

    import java.util.List;
    import java.util.Optional;
    import java.util.regex.Pattern;

    @Service
    @Transactional
    public class UserService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

        @Autowired
        public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
        }

        public User registerUser(User user) {
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
            if (user.getPassword().length() < 6) {
                throw new IllegalArgumentException("Password must be at least 6 characters long.");
            }
            if (!StringUtils.hasText(user.getFirstName())) {
                throw new IllegalArgumentException("First name cannot be empty.");
            }
            if (!StringUtils.hasText(user.getLastName())) {
                throw new IllegalArgumentException("Last name cannot be empty.");
            }
            if (user.getRole() == null) {
                user.setRole(User.UserRole.CUSTOMER);
            }

            // Hash the password before saving
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            return userRepository.save(user);
        }

        public Optional<User> authenticateUser(String email, String rawPassword) {
            if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword)) {
                return Optional.empty();
            }

            return userRepository.findByEmail(email)
                    .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()));
        }

        public List<User> getAllUsers() {
            return userRepository.findAll();
        }

        public Optional<User> getUserById(Long id) {
            return userRepository.findById(id);
        }

        public Optional<User> updateUser(Long id, User userDetails) {
            return userRepository.findById(id)
                    .map(existingUser -> {
                        if (!StringUtils.hasText(userDetails.getFirstName())) {
                            throw new IllegalArgumentException("First name cannot be empty.");
                        }
                        if (!StringUtils.hasText(userDetails.getLastName())) {
                            throw new IllegalArgumentException("Last name cannot be empty.");
                        }
                        if (StringUtils.hasText(userDetails.getEmail()) && !userDetails.getEmail().equals(existingUser.getEmail())) {
                            if (!EMAIL_PATTERN.matcher(userDetails.getEmail()).matches()) {
                                throw new IllegalArgumentException("Invalid email format for update.");
                            }
                            if (userRepository.findByEmail(userDetails.getEmail()).isPresent()) {
                                throw new IllegalArgumentException("New email already in use by another user.");
                            }
                            existingUser.setEmail(userDetails.getEmail());
                        }
                        if (userDetails.getRole() != null) {
                            existingUser.setRole(userDetails.getRole());
                        }
                        existingUser.setFirstName(userDetails.getFirstName());
                        existingUser.setLastName(userDetails.getLastName());
                        existingUser.setRoomNumber(userDetails.getRoomNumber());
                        existingUser.setPhoneNumber(userDetails.getPhoneNumber());

                        return userRepository.save(existingUser);
                    });
        }

        public boolean updatePassword(Long userId, String newPassword) {
            if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
                throw new IllegalArgumentException("New password must not be empty and be at least 6 characters long.");
            }
            return userRepository.findById(userId)
                    .map(user -> {
                        user.setPassword(passwordEncoder.encode(newPassword));
                        userRepository.save(user);
                        return true;
                    }).orElse(false);
        }

        public boolean deleteUser(Long id) {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
                return true;
            }
            return false;
        }
    }