package kasiKotas.service.passkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import kasiKotas.model.PasskeyCredential;
import kasiKotas.model.User;
import kasiKotas.model.WebAuthnChallenge;
import kasiKotas.repository.PasskeyCredentialRepository;
import kasiKotas.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasskeyServiceTest {

    @Mock
    private RelyingParty relyingParty;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;

    @Mock
    private WebAuthnChallengeStore challengeStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createRegistrationOptionsReturnsRequestIdAndPublicKey() {
        PasskeyService service = new PasskeyService(relyingParty, userRepository, passkeyCredentialRepository, challengeStore, objectMapper);
        User user = buildUser();
        PublicKeyCredentialCreationOptions options = mockCreationOptions();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(relyingParty.startRegistration(any())).thenReturn(options);
        when(challengeStore.putRegistrationRequest(user.getEmail(), user.getId(), options)).thenReturn("request-123");

        Map<String, Object> response = service.createRegistrationOptions(user.getEmail());

        assertEquals("request-123", response.get("requestId"));
        assertEquals(options, response.get("publicKey"));
        verify(challengeStore).putRegistrationRequest(user.getEmail(), user.getId(), options);
    }

    @Test
    void verifyRegistrationStoresCredential() throws Exception {
        PasskeyService service = spy(new PasskeyService(relyingParty, userRepository, passkeyCredentialRepository, challengeStore, objectMapper));
        User user = buildUser();
        PublicKeyCredentialCreationOptions request = mockCreationOptions();
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential = mockRegistrationCredential();
        RegistrationResult registrationResult = org.mockito.Mockito.mock(RegistrationResult.class);

        doReturn(credential).when(service).parseRegistrationCredential(any());
        when(challengeStore.consumeRegistrationRequest("request-123")).thenReturn(Optional.of(new WebAuthnChallengeStore.PendingRequest(
                WebAuthnChallenge.ChallengeType.REGISTRATION,
                user.getEmail(),
                user.getId(),
                request,
                Instant.now()
        )));
        when(relyingParty.finishRegistration(any())).thenReturn(registrationResult);
        when(registrationResult.getKeyId()).thenReturn(PublicKeyCredentialDescriptor.builder().id(new ByteArray(new byte[]{1, 2, 3})).build());
        when(registrationResult.getPublicKeyCose()).thenReturn(new ByteArray(new byte[]{4, 5, 6}));
        when(registrationResult.getSignatureCount()).thenReturn(7L);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.verifyRegistration("request-123", objectMapper.createObjectNode(), "My Passkey");

        ArgumentCaptor<PasskeyCredential> captor = ArgumentCaptor.forClass(PasskeyCredential.class);
        verify(passkeyCredentialRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals("My Passkey", captor.getValue().getNickname());
        assertEquals(7L, captor.getValue().getSignCount());
        assertNotNull(captor.getValue().getCredentialId());
    }

    @Test
    void createLoginOptionsReturnsRequestIdAndPublicKey() {
        PasskeyService service = new PasskeyService(relyingParty, userRepository, passkeyCredentialRepository, challengeStore, objectMapper);
        User user = buildUser();
        AssertionRequest assertionRequest = mockAssertionRequest();
        com.yubico.webauthn.data.PublicKeyCredentialRequestOptions publicKeyCredentialRequestOptions =
            org.mockito.Mockito.mock(com.yubico.webauthn.data.PublicKeyCredentialRequestOptions.class);

        when(assertionRequest.getPublicKeyCredentialRequestOptions()).thenReturn(publicKeyCredentialRequestOptions);

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passkeyCredentialRepository.countByUserId(user.getId())).thenReturn(1L);
        when(relyingParty.startAssertion(any())).thenReturn(assertionRequest);
        when(challengeStore.putAssertionRequest(user.getEmail(), user.getId(), assertionRequest)).thenReturn("request-456");

        Map<String, Object> response = service.createLoginOptions(user.getEmail());

        assertEquals("request-456", response.get("requestId"));
        assertEquals(publicKeyCredentialRequestOptions, response.get("publicKey"));
    }

    @Test
    void verifyLoginUpdatesStoredCredentialAndReturnsUser() throws Exception {
        PasskeyService service = spy(new PasskeyService(relyingParty, userRepository, passkeyCredentialRepository, challengeStore, objectMapper));
        User user = buildUser();
        AssertionRequest assertionRequest = mockAssertionRequest();
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential = mockAssertionCredential();
        AssertionResult assertionResult = org.mockito.Mockito.mock(AssertionResult.class);
        PasskeyCredential storedCredential = PasskeyCredential.builder()
                .id(10L)
                .user(user)
                .credentialId("credential-123")
                .publicKey("public-key")
                .signCount(1L)
                .createdAt(LocalDateTime.now())
                .build();

        doReturn(credential).when(service).parseAssertionCredential(any());
        when(challengeStore.consumeAssertionRequest("request-456")).thenReturn(Optional.of(new WebAuthnChallengeStore.PendingRequest(
                WebAuthnChallenge.ChallengeType.ASSERTION,
                user.getEmail(),
                user.getId(),
                assertionRequest,
                Instant.now()
        )));
        when(relyingParty.finishAssertion(any())).thenReturn(assertionResult);
        when(assertionResult.isSuccess()).thenReturn(true);
        when(assertionResult.getUsername()).thenReturn(user.getEmail());
        when(assertionResult.getCredentialId()).thenReturn(new ByteArray("credential-123".getBytes()));
        when(assertionResult.getSignatureCount()).thenReturn(9L);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passkeyCredentialRepository.findByCredentialId(anyString())).thenReturn(Optional.of(storedCredential));

        User returned = service.verifyLogin("request-456", objectMapper.createObjectNode());

        assertEquals(user, returned);
        assertEquals(9L, storedCredential.getSignCount());
        verify(passkeyCredentialRepository).save(storedCredential);
    }

    @Test
    void verifyLoginRejectsUnknownCredential() throws Exception {
        PasskeyService service = spy(new PasskeyService(relyingParty, userRepository, passkeyCredentialRepository, challengeStore, objectMapper));
        User user = buildUser();
        AssertionRequest assertionRequest = mockAssertionRequest();
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential = mockAssertionCredential();
        AssertionResult assertionResult = org.mockito.Mockito.mock(AssertionResult.class);

        doReturn(credential).when(service).parseAssertionCredential(any());
        when(challengeStore.consumeAssertionRequest("request-456")).thenReturn(Optional.of(new WebAuthnChallengeStore.PendingRequest(
                WebAuthnChallenge.ChallengeType.ASSERTION,
                user.getEmail(),
                user.getId(),
                assertionRequest,
                Instant.now()
        )));
        when(relyingParty.finishAssertion(any())).thenReturn(assertionResult);
        when(assertionResult.isSuccess()).thenReturn(true);
        when(assertionResult.getUsername()).thenReturn(user.getEmail());
        when(assertionResult.getCredentialId()).thenReturn(new ByteArray("unknown-credential".getBytes()));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passkeyCredentialRepository.findByCredentialId(anyString())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.verifyLogin("request-456", objectMapper.createObjectNode())
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .password("password")
                .firstName("John")
                .lastName("Doe")
                .role(User.UserRole.CUSTOMER)
                .build();
    }

    private PublicKeyCredentialCreationOptions mockCreationOptions() {
        return org.mockito.Mockito.mock(PublicKeyCredentialCreationOptions.class);
    }

    private AssertionRequest mockAssertionRequest() {
        return org.mockito.Mockito.mock(AssertionRequest.class);
    }

    private PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> mockRegistrationCredential() {
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential = org.mockito.Mockito.mock(PublicKeyCredential.class);
        AuthenticatorAttestationResponse response = org.mockito.Mockito.mock(AuthenticatorAttestationResponse.class);
        when(response.getTransports()).thenReturn(new java.util.TreeSet<>(List.of(AuthenticatorTransport.INTERNAL)));
        when(credential.getResponse()).thenReturn(response);
        return credential;
    }

    private PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> mockAssertionCredential() {
        return org.mockito.Mockito.mock(PublicKeyCredential.class);
    }
}