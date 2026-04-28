package kasiKotas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import kasiKotas.model.User;
import kasiKotas.service.UserService;
import kasiKotas.security.JwtUtil;
import kasiKotas.service.passkey.PasskeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private PasskeyService passkeyService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        return userService.authenticateUser(email, password)
                .map(user -> {
                    // FIXED: Pass both email AND role to generateToken
                    String token = jwtUtil.generateToken(user.getEmail(), user.getRole().toString());
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Login successful");
                    response.put("token", token);
                    response.put("id", user.getId());
                    response.put("firstName", user.getFirstName());
                    response.put("role", user.getRole());
                    // Add other user fields as needed
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("message", "Invalid credentials")));
    }

    @PostMapping("/passkey/register/options")
    public ResponseEntity<?> passkeyRegisterOptions(@RequestBody(required = false) Map<String, String> request) {
        String email = resolvePasskeyRegistrationEmail(request);
        return ResponseEntity.ok(passkeyService.createRegistrationOptions(email));
    }

    @PostMapping("/passkey/register/verify")
    public ResponseEntity<?> passkeyRegisterVerify(@RequestBody Map<String, Object> request) {
        String requestId = (String) request.get("requestId");
        String nickname = (String) request.get("nickname");
        Object credentialObj = request.get("credential");

        if (requestId == null || credentialObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", "requestId and credential are required"));
        }

        JsonNode credentialNode = toJsonNode(credentialObj);
        passkeyService.verifyRegistration(requestId, credentialNode, nickname);

        return ResponseEntity.ok(Collections.singletonMap("message", "Passkey registered successfully"));
    }

    @PostMapping("/passkey/login/options")
    public ResponseEntity<?> passkeyLoginOptions(@RequestBody Map<String, String> requestBody,
                                                 HttpServletRequest request) {
        String email = requestBody.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", "email is required"));
        }

        System.out.println("DEBUG passkeyLoginOptions called: Origin=" + request.getHeader("Origin")
                + ", RemoteAddr=" + request.getRemoteAddr() + ", URI=" + request.getRequestURI());
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                System.out.println("HEADER: " + name + " = " + request.getHeader(name));
            }
        }

        System.out.println("DEBUG passkeyLoginOptions before service call for email=" + email);
        ResponseEntity<?> response = ResponseEntity.ok(passkeyService.createLoginOptions(email));
        System.out.println("DEBUG passkeyLoginOptions after service call for email=" + email);
        return response;
    }

    @PostMapping("/passkey/login/verify")
    public ResponseEntity<?> passkeyLoginVerify(@RequestBody Map<String, Object> request) {
        String requestId = (String) request.get("requestId");
        Object credentialObj = request.get("credential");

        if (requestId == null || credentialObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", "requestId and credential are required"));
        }

        User user = passkeyService.verifyLogin(requestId, toJsonNode(credentialObj));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().toString());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("id", user.getId());
        response.put("firstName", user.getFirstName());
        response.put("role", user.getRole());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/passkey")
    public ResponseEntity<?> listPasskeys() {
        String email = resolveAuthenticatedEmail();
        return userService.getUserByEmail(email)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(passkeyService.listPasskeys(user.getId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("message", "User not found")));
    }

    @DeleteMapping("/passkey/{passkeyId}")
    public ResponseEntity<?> deletePasskey(@PathVariable Long passkeyId) {
        String email = resolveAuthenticatedEmail();
        return userService.getUserByEmail(email)
                .<ResponseEntity<?>>map(user -> {
                    passkeyService.deletePasskey(user.getId(), passkeyId);
                    return ResponseEntity.ok(Collections.singletonMap("message", "Passkey deleted"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("message", "User not found")));
    }

    private String resolveAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String username && !"anonymousUser".equalsIgnoreCase(username)) {
            return username;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private String resolvePasskeyRegistrationEmail(Map<String, String> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            }

            if (principal instanceof String username && !"anonymousUser".equalsIgnoreCase(username)) {
                return username;
            }
        }

        if (request != null) {
            String email = request.get("email");
            if (email != null && !email.isBlank()) {
                return email.trim();
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required for passkey registration");
    }

    private JsonNode toJsonNode(Object value) {
        return objectMapper.valueToTree(value);
    }
}
