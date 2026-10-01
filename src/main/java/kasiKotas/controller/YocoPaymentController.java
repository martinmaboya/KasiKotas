package kasiKotas.controller;

import kasiKotas.model.PaymentStatus;
import kasiKotas.model.User;
import kasiKotas.service.PaymentService;
import kasiKotas.service.UserService;
import kasiKotas.service.YocoPaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/yoco")
@RequiredArgsConstructor
@Slf4j
public class YocoPaymentController {

    private final YocoPaymentService yocoPaymentService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${yoco.webhook-secret:}")
    private String yocoWebhookSecret;

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
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @PathVariable Long intentId,
            @RequestParam(value = "checkoutId", required = false) String checkoutId) {
        // Confirm the intent immediately on the success redirect — no webhook needed.
        // confirmIntent() is idempotent: if already PAID it returns instantly.
        Map<String, Object> result = yocoPaymentService.confirmAndGetStatus(intentId, checkoutId);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/cancel/{intentId}")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long intentId) {
        yocoPaymentService.cancelIntent(intentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleYocoWebhook(HttpServletRequest request) {
        // Read raw body bytes — required for HMAC verification before parsing.
        byte[] rawBody;
        try {
            rawBody = request.getInputStream().readAllBytes();
        } catch (Exception ex) {
            log.error("Yoco webhook: failed to read request body: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        // Verify HMAC-SHA256 signature if a webhook secret is configured.
        if (yocoWebhookSecret != null && !yocoWebhookSecret.isBlank()) {
            String signature = request.getHeader("X-Yoco-Signature");
            if (!isValidSignature(rawBody, signature)) {
                log.warn("Yoco webhook: invalid or missing signature — request rejected.");
                // Return 200 to avoid leaking information; the request is simply ignored.
                return ResponseEntity.ok().build();
            }
        }

        // Parse the verified body into a map.
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Yoco webhook: failed to parse JSON body: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        if (payload == null || !payload.containsKey("type")) {
            return ResponseEntity.ok().build();
        }

        String eventType = String.valueOf(payload.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("payload");
        if (data == null) {
            // Keep accepting the legacy envelope used by older Yoco events/tests.
            data = (Map<String, Object>) payload.get("data");
        }
        if (data == null) {
            return ResponseEntity.ok().build();
        }

        String checkoutId = data.get("id") == null ? null : data.get("id").toString();

        if ("checkout.succeeded".equalsIgnoreCase(eventType)
                || "payment.succeeded".equalsIgnoreCase(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
            try {
                if (metadata != null && metadata.get("intentId") != null) {
                    yocoPaymentService.confirmIntent(Long.parseLong(metadata.get("intentId").toString()), checkoutId);
                } else if (checkoutId != null && !checkoutId.isBlank()) {
                    yocoPaymentService.confirmIntentByCheckoutId(checkoutId);
                } else {
                    log.error("Yoco webhook: checkout.succeeded received but no intentId in metadata and no checkoutId. payload={}", payload);
                }
            } catch (Exception ex) {
                // Always return 200 so Yoco does not keep retrying.
                // Log the failure for manual review — the customer's payment exists on Yoco's side.
                log.error("Yoco webhook: failed to confirm intent for checkoutId={}, error={}", checkoutId, ex.getMessage(), ex);
            }
            return ResponseEntity.ok().build();
        }

        if ("checkout.failed".equalsIgnoreCase(eventType)
                || "payment.failed".equalsIgnoreCase(eventType)
                || "checkout.cancelled".equalsIgnoreCase(eventType)
                || "payment.cancelled".equalsIgnoreCase(eventType)) {
            if (checkoutId != null && !checkoutId.isBlank()) {
                try {
                    paymentService.updatePaymentStatusByYocoCheckoutId(checkoutId, PaymentStatus.FAILED);
                } catch (Exception ex) {
                    log.warn("Yoco webhook: could not update failed status for checkoutId={}: {}", checkoutId, ex.getMessage());
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Verifies the Yoco webhook signature.
     * Yoco signs the raw request body with HMAC-SHA256 using the webhook secret
     * and sends the hex digest in the {@code X-Yoco-Signature} header.
     */
    private boolean isValidSignature(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Yoco webhook: X-Yoco-Signature header is missing.");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(yocoWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(rawBody);
            String expected = HexFormat.of().formatHex(expectedBytes);
            // Constant-time comparison to prevent timing attacks.
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Yoco webhook: HMAC verification error: {}", ex.getMessage());
            return false;
        }
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
