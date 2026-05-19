package kasiKotas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;

/**
 * Encryption utility for securing sensitive banking information.
 * Uses AES-256 encryption to protect bank account details at rest.
 * 
 * Security Features:
 * - AES-256-bit encryption algorithm
 * - Encryption key sourced from environment variables (never hardcoded)
 * - Automatic verification checksums to detect tampering
 * - Separate checksums for each sensitive field
 */
@Component
public class BankDetailsEncryption {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE_BYTES = 32;
    private final SecretKey encryptionKey;

    public BankDetailsEncryption(@Value("${app.security.bank-encryption-key}") String keyString) {
        try {
            this.encryptionKey = new SecretKeySpec(resolveKeyBytes(keyString), ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to initialize bank details encryption key: " + e.getMessage(), e);
        }
    }

    private byte[] resolveKeyBytes(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be blank.");
        }

        String trimmed = keyString.trim();

        try {
            byte[] decodedKey = Base64.getDecoder().decode(trimmed);
            if (decodedKey.length == KEY_SIZE_BYTES) {
                return decodedKey;
            }
            return sha256(decodedKey);
        } catch (IllegalArgumentException ex) {
            return sha256(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to derive encryption key: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts sensitive bank data using AES-256.
     * @param plaintext The data to encrypt
     * @return Base64-encoded encrypted data
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext; // Don't encrypt empty values
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt bank details: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts AES-256 encrypted bank data.
     * @param encryptedData Base64-encoded encrypted data
     * @return Decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData; // Don't decrypt empty values
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt bank details. Data may be corrupted or tampered with: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a SHA-256 checksum/hash for data integrity verification.
     * This helps detect if data has been tampered with.
     * @param data The data to checksum
     * @return Hex-encoded SHA-256 hash
     */
    public String generateChecksum(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            
            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate checksum: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies that a value matches its checksum.
     * @param data The original data
     * @param checksum The expected checksum
     * @return true if the checksum matches, false if data has been tampered with
     */
    public boolean verifyChecksum(String data, String checksum) {
        if (checksum == null) {
            return data == null || data.isEmpty();
        }
        String computed = generateChecksum(data);
        return checksum.equals(computed);
    }

    /**
     * Generates a new encryption key (for initialization/rotation purposes).
     * This is not used at runtime but can be called to generate a new key.
     * @return Base64-encoded 256-bit encryption key
     */
    public static String generateNewEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256); // 256-bit key
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key: " + e.getMessage(), e);
        }
    }
}

