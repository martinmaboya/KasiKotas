package kasiKotas.service.passkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import kasiKotas.model.PasskeyCredential;
import kasiKotas.model.User;
import kasiKotas.repository.PasskeyCredentialRepository;
import kasiKotas.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PasskeyService {

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final WebAuthnChallengeStore challengeStore;
    private final ObjectMapper objectMapper;

    public PasskeyService(
            RelyingParty relyingParty,
            UserRepository userRepository,
            PasskeyCredentialRepository passkeyCredentialRepository,
            WebAuthnChallengeStore challengeStore,
            ObjectMapper objectMapper
    ) {
        this.relyingParty = relyingParty;
        this.userRepository = userRepository;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.challengeStore = challengeStore;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createRegistrationOptions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return createRegistrationOptions(user);
    }

    public Map<String, Object> createRegistrationOptions(User user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required for passkey registration");
        }

        if (passkeyCredentialRepository.countByUserId(user.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passkey already enrolled for this user");
        }

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getFirstName() + " " + user.getLastName())
                .id(new ByteArray(WebAuthnCredentialRepositoryAdapter.longToBytes(user.getId())))
                .build();

        PublicKeyCredentialCreationOptions registrationRequest = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                    .userVerification(UserVerificationRequirement.PREFERRED)
                    .build())
                        .build()
        );

        String requestId = challengeStore.putRegistrationRequest(user.getEmail(), user.getId(), registrationRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("publicKey", registrationRequest);
        return response;
    }

    public boolean hasPasskeyEnrollment(Long userId) {
        if (userId == null) {
            return false;
        }
        return passkeyCredentialRepository.countByUserId(userId) > 0;
    }

    public void verifyRegistration(String requestId, JsonNode credentialNode, String nickname) {
        WebAuthnChallengeStore.PendingRequest pendingRequest = challengeStore.consumeRegistrationRequest(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration challenge missing or expired"));

        PublicKeyCredentialCreationOptions registrationRequest = (PublicKeyCredentialCreationOptions) pendingRequest.request();

        // Verbose diagnostics to help debug production failures where finishRegistration isn't logging
        try {
            System.out.println("DEBUG verifyRegistration requestId=" + requestId + " userId=" + pendingRequest.userId() + " email=" + pendingRequest.email());
            System.out.println("DEBUG verifyRegistration stored request JSON (truncated): " + (pendingRequest.requestJson() == null ? "<null>" : pendingRequest.requestJson().replaceAll("\\s+"," ").substring(0, Math.min(800, pendingRequest.requestJson().length()))));
        } catch (Exception ex) {
            System.out.println("DEBUG verifyRegistration: failed to print stored requestJson: " + ex.getMessage());
        }

        try {
            String credentialStr = objectMapper.writeValueAsString(credentialNode);
            System.out.println("DEBUG verifyRegistration incoming credential (truncated): " + credentialStr.replaceAll("\\s+", " ").substring(0, Math.min(1200, credentialStr.length())));

            JsonNode idNode = credentialNode.get("id");
            JsonNode rawIdNode = credentialNode.get("rawId");
            JsonNode responseNode = credentialNode.get("response");
            JsonNode attestationObjectNode = responseNode == null ? null : responseNode.get("attestationObject");
            JsonNode clientDataJSONNode = responseNode == null ? null : responseNode.get("clientDataJSON");

            System.out.println("DEBUG verifyRegistration fields present: id=" + (idNode != null) + " rawId=" + (rawIdNode != null) + " attestationObject=" + (attestationObjectNode != null) + " clientDataJSON=" + (clientDataJSONNode != null));

            if (attestationObjectNode != null && attestationObjectNode.isTextual()) {
                System.out.println("DEBUG verifyRegistration attestationObject length=" + attestationObjectNode.asText().length());
            }
            if (clientDataJSONNode != null && clientDataJSONNode.isTextual()) {
                System.out.println("DEBUG verifyRegistration clientDataJSON length=" + clientDataJSONNode.asText().length());
            }
        } catch (Exception ex) {
            System.out.println("DEBUG verifyRegistration: failed to print incoming credential: " + ex.getMessage());
        }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential = parseRegistrationCredential(credentialNode);

        System.out.println("DEBUG verifyRegistration calling finishRegistration for requestId=" + requestId);
        var result = finishRegistration(registrationRequest, credential);
        System.out.println("DEBUG verifyRegistration finishRegistration succeeded for requestId=" + requestId + ", credentialId=" + result.getKeyId().getId().getBase64Url());

        Optional<User> userOpt = userRepository.findById(pendingRequest.userId());
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        var transports = credential.getResponse().getTransports();
        String transportsValue = transports == null
            ? ""
            : transports.stream().map(AuthenticatorTransport::toString).collect(Collectors.joining(","));

        PasskeyCredential passkeyCredential = PasskeyCredential.builder()
                .user(userOpt.get())
                .credentialId(result.getKeyId().getId().getBase64Url())
                .publicKey(result.getPublicKeyCose().getBase64Url())
                .signCount(result.getSignatureCount())
            .transports(transportsValue)
                .nickname(nickname)
                .createdAt(LocalDateTime.now())
                .lastUsedAt(null)
                .build();

        passkeyCredentialRepository.save(passkeyCredential);
        System.out.println("DEBUG verifyRegistration saved passkey credential for userId=" + pendingRequest.userId() + ", nickname=" + nickname);
    }

    public Map<String, Object> createLoginOptions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        System.out.println("DEBUG createLoginOptions user found id=" + user.getId() + ", email=" + user.getEmail());

        long passkeyCount = passkeyCredentialRepository.countByUserId(user.getId());
        System.out.println("DEBUG createLoginOptions passkeyCount=" + passkeyCount + " for userId=" + user.getId());
        if (passkeyCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No passkey enrolled for this user");
        }

        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(user.getEmail())
                .userVerification(UserVerificationRequirement.PREFERRED)
                        .build()
        );

            System.out.println("DEBUG createLoginOptions startAssertion completed for userId=" + user.getId());

        String requestId = challengeStore.putAssertionRequest(user.getEmail(), user.getId(), assertionRequest);

            System.out.println("DEBUG createLoginOptions challenge stored requestId=" + requestId + " for userId=" + user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("publicKey", assertionRequest.getPublicKeyCredentialRequestOptions());
        return response;
    }

    public User verifyLogin(String requestId, JsonNode credentialNode) {
        WebAuthnChallengeStore.PendingRequest pendingRequest = challengeStore.consumeAssertionRequest(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login challenge missing or expired"));

        AssertionRequest assertionRequest = (AssertionRequest) pendingRequest.request();

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential = parseAssertionCredential(credentialNode);

        AssertionResult result = finishAssertion(assertionRequest, credential);

        if (!result.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey assertion verification failed");
        }

        String userEmail = result.getUsername();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        PasskeyCredential storedCredential = passkeyCredentialRepository.findByCredentialId(result.getCredentialId().getBase64Url())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown credential"));

        storedCredential.setSignCount(result.getSignatureCount());
        storedCredential.setLastUsedAt(LocalDateTime.now());
        passkeyCredentialRepository.save(storedCredential);

        return user;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPasskeys(Long userId) {
        return passkeyCredentialRepository.findByUserId(userId)
                .stream()
                .map(passkey -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", passkey.getId());
                    row.put("credentialId", passkey.getCredentialId());
                    row.put("nickname", passkey.getNickname());
                    row.put("transports", passkey.getTransports());
                    row.put("createdAt", passkey.getCreatedAt());
                    row.put("lastUsedAt", passkey.getLastUsedAt());
                    return row;
                })
                .toList();
    }

    public void deletePasskey(Long userId, Long passkeyId) {
        PasskeyCredential passkey = passkeyCredentialRepository.findById(passkeyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey not found"));

        if (!passkey.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's passkey");
        }

        passkeyCredentialRepository.delete(passkey);
    }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> parseRegistrationCredential(
            JsonNode credentialNode
    ) {
        try {
            return PublicKeyCredential.parseRegistrationResponseJson(objectMapper.writeValueAsString(credentialNode));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid registration credential payload");
        }
    }

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> parseAssertionCredential(
            JsonNode credentialNode
    ) {
        try {
            return PublicKeyCredential.parseAssertionResponseJson(objectMapper.writeValueAsString(credentialNode));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid assertion credential payload");
        }
    }

    private com.yubico.webauthn.RegistrationResult finishRegistration(
            PublicKeyCredentialCreationOptions request,
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential
    ) {
        try {
            return relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(credential)
                    .build());
        } catch (RegistrationFailedException e) {
            logRegistrationFailure("RegistrationFailedException", e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey registration verification failed");
        } catch (RuntimeException e) {
            logRegistrationFailure("RuntimeException", e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey registration verification failed");
        }
    }

    private void logRegistrationFailure(String label, Exception exception) {
        System.out.println("DEBUG finishRegistration failed [" + label + "]: " + exception.getClass().getName() + " - " + exception.getMessage());
        Throwable cause = exception.getCause();
        int depth = 0;
        while (cause != null && depth < 4) {
            System.out.println("DEBUG finishRegistration cause[" + depth + "]: " + cause.getClass().getName() + " - " + cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        exception.printStackTrace();
    }

    private AssertionResult finishAssertion(
            AssertionRequest request,
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential
    ) {
        try {
            return relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(request)
                            .response(credential)
                            .build()
            );
        } catch (AssertionFailedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey assertion verification failed");
        }
    }
}
