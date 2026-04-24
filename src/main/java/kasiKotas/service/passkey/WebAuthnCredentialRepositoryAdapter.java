package kasiKotas.service.passkey;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import kasiKotas.model.PasskeyCredential;
import kasiKotas.repository.PasskeyCredentialRepository;
import kasiKotas.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebAuthnCredentialRepositoryAdapter implements CredentialRepository {

    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final UserRepository userRepository;

    public WebAuthnCredentialRepositoryAdapter(
            PasskeyCredentialRepository passkeyCredentialRepository,
            UserRepository userRepository
    ) {
        this.passkeyCredentialRepository = passkeyCredentialRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(user -> passkeyCredentialRepository.findByUserId(user.getId()).stream()
                .map(this::toPublicKeyCredentialDescriptor)
                .flatMap(Optional::stream)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(user -> new ByteArray(longToBytes(user.getId())));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        Long userId = bytesToLong(userHandle.getBytes());
        return userRepository.findById(userId).map(user -> user.getEmail());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Long userId = bytesToLong(userHandle.getBytes());

        return passkeyCredentialRepository.findByCredentialId(credentialId.getBase64Url())
                .filter(credential -> credential.getUser().getId().equals(userId))
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeyCredentialRepository.findByCredentialId(credentialId.getBase64Url())
                .map(this::toRegisteredCredential)
                .map(Set::of)
                .orElse(Collections.emptySet());
    }

    public List<PasskeyCredential> getByUserId(Long userId) {
        return passkeyCredentialRepository.findByUserId(userId);
    }

    private RegisteredCredential toRegisteredCredential(PasskeyCredential credential) {
        ByteArray credentialId = decodeBase64Url(credential.getCredentialId());
        ByteArray publicKey = decodeBase64Url(credential.getPublicKey());

        return RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(new ByteArray(longToBytes(credential.getUser().getId())))
                .publicKeyCose(publicKey)
                .signatureCount(credential.getSignCount())
                .build();
    }

    private Optional<PublicKeyCredentialDescriptor> toPublicKeyCredentialDescriptor(PasskeyCredential credential) {
        try {
            ByteArray credentialId = ByteArray.fromBase64Url(credential.getCredentialId());
            return Optional.of(PublicKeyCredentialDescriptor.builder().id(credentialId).build());
        } catch (Base64UrlException e) {
            return Optional.empty();
        }
    }

    private ByteArray decodeBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Invalid Base64Url credential data in database", e);
        }
    }

    public static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    public static long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }
}
