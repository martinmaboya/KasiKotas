package kasiKotas.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BankDetailsEncryptionTest {

    @Test
    void constructorAcceptsPlainTextSecretAndRoundTrips() {
        BankDetailsEncryption encryption = new BankDetailsEncryption("render-secret-that-is-not-valid-base64-but-must-not-crash");

        String plaintext = "Demo Bank Account 123456789";
        String encrypted = encryption.encrypt(plaintext);

        assertEquals(plaintext, encryption.decrypt(encrypted));
    }

    @Test
    void constructorAcceptsBase64KeyAndRoundTrips() {
        String key = assertDoesNotThrow(BankDetailsEncryption::generateNewEncryptionKey);
        BankDetailsEncryption encryption = new BankDetailsEncryption(key);

        String plaintext = "Another bank detail payload";
        String encrypted = encryption.encrypt(plaintext);

        assertEquals(plaintext, encryption.decrypt(encrypted));
    }
}

