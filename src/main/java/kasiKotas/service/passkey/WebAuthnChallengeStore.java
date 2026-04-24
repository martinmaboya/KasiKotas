package kasiKotas.service.passkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class WebAuthnChallengeStore {

    private final Cache<String, PendingRequest> requestCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public String putRegistrationRequest(String email, Long userId, PublicKeyCredentialCreationOptions request) {
        String requestId = UUID.randomUUID().toString();
        requestCache.put(requestId, new PendingRequest(PendingType.REGISTRATION, email, userId, request, Instant.now()));
        return requestId;
    }

    public String putAssertionRequest(String email, AssertionRequest request) {
        String requestId = UUID.randomUUID().toString();
        requestCache.put(requestId, new PendingRequest(PendingType.ASSERTION, email, null, request, Instant.now()));
        return requestId;
    }

    public Optional<PendingRequest> consumeRegistrationRequest(String requestId) {
        return consume(requestId, PendingType.REGISTRATION);
    }

    public Optional<PendingRequest> consumeAssertionRequest(String requestId) {
        return consume(requestId, PendingType.ASSERTION);
    }

    private Optional<PendingRequest> consume(String requestId, PendingType expectedType) {
        PendingRequest pending = requestCache.getIfPresent(requestId);
        if (pending == null || pending.type() != expectedType) {
            return Optional.empty();
        }
        requestCache.invalidate(requestId);
        return Optional.of(pending);
    }

    public enum PendingType {
        REGISTRATION,
        ASSERTION
    }

    public record PendingRequest(
            PendingType type,
            String email,
            Long userId,
            Object request,
            Instant createdAt
    ) {
    }
}
