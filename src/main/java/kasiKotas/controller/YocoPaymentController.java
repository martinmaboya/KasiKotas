package kasiKotas.controller;

import kasiKotas.model.User;
import kasiKotas.service.UserService;
import kasiKotas.service.YocoPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/yoco")
@RequiredArgsConstructor
public class YocoPaymentController {

    private final YocoPaymentService yocoPaymentService;
    private final UserService userService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create-checkout")
    public ResponseEntity<Map<String, String>> createCheckout(@RequestBody CheckoutRequest request) {
        if (request == null || request.getOrder() == null) {
            throw new IllegalArgumentException("order is required.");
        }
        User user = currentUser();
        String redirectUrl = yocoPaymentService.createCheckoutSession(
                request.getOrder(), user, request.getSuccessUrl(), request.getCancelUrl());
        return ResponseEntity.ok(Map.of(
            "redirectUrl", redirectUrl));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/verify/{intentId}")
    public ResponseEntity<Map<String, Object>> verifyPayment(@PathVariable Long intentId) {
        return ResponseEntity.ok(Map.of(
                "status", "PENDING",
                "intentId", intentId,
                "message", "Payment status is finalized by the Yoco webhook."));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/cancel/{intentId}")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long intentId) {
        yocoPaymentService.cancelIntent(intentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleYocoWebhook(@RequestBody Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("type")) {
            return ResponseEntity.ok().build();
        }

        String eventType = String.valueOf(payload.get("type"));
        if (!"checkout.succeeded".equalsIgnoreCase(eventType)
                && !"payment.succeeded".equalsIgnoreCase(eventType)) {
            return ResponseEntity.ok().build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data == null) {
            return ResponseEntity.ok().build();
        }
        String checkoutId = data.get("id") == null ? null : data.get("id").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        if (metadata != null && metadata.get("intentId") != null) {
            yocoPaymentService.confirmIntent(Long.parseLong(metadata.get("intentId").toString()), checkoutId);
        }
        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        String email = principal instanceof UserDetails details
                ? details.getUsername()
                : principal instanceof String value ? value : null;
        return userService.getUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    @Data
    public static class CheckoutRequest {
        private Map<String, Object> order;
        private String successUrl;
        private String cancelUrl;
    }
}
