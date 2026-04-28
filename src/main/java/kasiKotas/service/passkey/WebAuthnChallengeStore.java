package kasiKotas.service.passkey;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import kasiKotas.model.WebAuthnChallenge;
import kasiKotas.repository.WebAuthnChallengeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class WebAuthnChallengeStore {

    private final WebAuthnChallengeRepository challengeRepository;
    private final Duration challengeTtl;

    public WebAuthnChallengeStore(
            WebAuthnChallengeRepository challengeRepository,
            @Value("${webauthn.challenge.ttlMs:300000}") long challengeTtlMs
    ) {
        this.challengeRepository = challengeRepository;
        this.challengeTtl = Duration.ofMillis(challengeTtlMs);
    }

    public String putRegistrationRequest(String email, Long userId, PublicKeyCredentialCreationOptions request) {
        String requestId = UUID.randomUUID().toString();
        challengeRepository.save(WebAuthnChallenge.builder()
                .requestId(requestId)
                .email(email)
                .userId(userId)
                .challenge(request.getChallenge().getBase64Url())
                .type(WebAuthnChallenge.ChallengeType.REGISTRATION)
                .requestJson(toJson(request))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(challengeTtl))
                .build());
        return requestId;
    }

    public String putAssertionRequest(String email, Long userId, AssertionRequest request) {
        String requestId = UUID.randomUUID().toString();
        challengeRepository.save(WebAuthnChallenge.builder()
                .requestId(requestId)
                .email(email)
                .userId(userId)
                .challenge(request.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url())
                .type(WebAuthnChallenge.ChallengeType.ASSERTION)
                .requestJson(toJson(request))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(challengeTtl))
                .build());
        return requestId;
    }

    public Optional<PendingRequest> consumeRegistrationRequest(String requestId) {
        return consume(requestId, WebAuthnChallenge.ChallengeType.REGISTRATION);
    }

    public Optional<PendingRequest> consumeAssertionRequest(String requestId) {
        return consume(requestId, WebAuthnChallenge.ChallengeType.ASSERTION);
    }

    private Optional<PendingRequest> consume(String requestId, WebAuthnChallenge.ChallengeType expectedType) {
        WebAuthnChallenge challenge = challengeRepository.findById(requestId).orElse(null);
        if (challenge == null || challenge.getType() != expectedType) {
            return Optional.empty();
        }
        if (challenge.isExpired()) {
            challengeRepository.delete(challenge);
            throw new ResponseStatusException(HttpStatus.GONE, "WebAuthn challenge expired");
        }

        challengeRepository.delete(challenge);

        Object request = expectedType == WebAuthnChallenge.ChallengeType.REGISTRATION
                ? fromJsonRegistration(challenge.getRequestJson())
                : fromJsonAssertion(challenge.getRequestJson());

        return Optional.of(new PendingRequest(
            expectedType,
            challenge.getEmail(),
            challenge.getUserId(),
            request,
            challenge.getRequestJson(),
            challenge.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        ));
    }

        public record PendingRequest(
            WebAuthnChallenge.ChallengeType type,
            String email,
            Long userId,
            Object request,
            String requestJson,
            Instant createdAt
        ) {
        }

    private String toJson(Object request) {
        try {
            if (request instanceof PublicKeyCredentialCreationOptions options) {
                return options.toJson();
            }
            if (request instanceof AssertionRequest assertionRequest) {
                return assertionRequest.toJson();
            }
            throw new IllegalArgumentException("Unsupported WebAuthn request type: " + request.getClass().getName());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebAuthn request", e);
        }
    }

    private PublicKeyCredentialCreationOptions fromJsonRegistration(String requestJson) {
        try {
            return PublicKeyCredentialCreationOptions.fromJson(requestJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore registration request", e);
        }
    }

    private AssertionRequest fromJsonAssertion(String requestJson) {
        try {
            return AssertionRequest.fromJson(requestJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore assertion request", e);
        }
    }
}
