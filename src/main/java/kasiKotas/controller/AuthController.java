package kasiKotas.controller;

import kasiKotas.service.UserService;
import kasiKotas.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        return userService.authenticateUser(email, password)
                .map(user -> {
                    String token = jwtUtil.generateToken(user.getEmail());
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Login successful");
                    response.put("token", token);
                    response.put("id", user.getId());
                    response.put("firstName", user.getFirstName());
                    response.put("role", user.getRole()); // Make sure this returns "ADMIN" or "USER"
                    // Add other user fields as needed
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("message", "Invalid credentials")));
    }
}