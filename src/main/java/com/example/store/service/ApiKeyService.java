package com.example.store.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates and validates HMAC-SHA256 signed API keys with a cryptographic random salt.
 *
 * <p>Key format: {@code sk_<clientId>_<timestamp>_<salt>_<signature>}
 *
 * <p>The signature is HMAC-SHA256(secret, "clientId.timestamp.salt") encoded as URL-safe Base64. The salt is a 16-byte
 * random value (also Base64-encoded) generated at key creation time, making each key unique and unpredictable even for
 * the same clientId and timestamp.
 *
 * <p>Validation recomputes the HMAC using the embedded salt and performs constant-time comparison — no database lookup
 * needed.
 */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "sk";
    private static final String SEPARATOR = "_";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int EXPECTED_PARTS = 5; // sk, clientId, timestamp, salt, signature
    private static final int SALT_BYTES = 16;

    private final byte[] secretBytes;
    private final SecureRandom secureRandom;

    public ApiKeyService(@Value("${app.security.api-key}") String signingSecret) {
        this.secretBytes = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a signed API key for the given client ID.
     *
     * <p>Each call produces a unique key due to the random salt, even for the same clientId.
     *
     * @param clientId unique identifier for the API consumer
     * @return a signed API key in the format sk_clientId_timestamp_salt_signature
     */
    public String generateKey(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String salt = generateSalt();
        String signature = computeSignature(clientId, timestamp, salt);
        return KEY_PREFIX + SEPARATOR + clientId + SEPARATOR + timestamp + SEPARATOR + salt + SEPARATOR + signature;
    }

    /**
     * Validates an API key by parsing it and recomputing the HMAC signature.
     *
     * @param apiKey the full API key string
     * @return true if the key structure is valid and the signature matches
     */
    public boolean validateKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }

        String[] parts = apiKey.split(SEPARATOR, EXPECTED_PARTS);
        if (parts.length != EXPECTED_PARTS) {
            return false;
        }

        String prefix = parts[0];
        String clientId = parts[1];
        String timestamp = parts[2];
        String salt = parts[3];
        String providedSignature = parts[4];

        if (!KEY_PREFIX.equals(prefix)) {
            return false;
        }

        if (clientId.isBlank() || timestamp.isBlank() || salt.isBlank() || providedSignature.isBlank()) {
            return false;
        }

        String expectedSignature = computeSignature(clientId, timestamp, salt);

        // Constant-time comparison to prevent timing attacks
        byte[] providedBytes = providedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    /**
     * Extracts the client ID from a valid API key.
     *
     * @param apiKey the full API key string (must be valid)
     * @return the client ID, or null if the key is malformed
     */
    public String extractClientId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String[] parts = apiKey.split(SEPARATOR, EXPECTED_PARTS);
        if (parts.length != EXPECTED_PARTS) {
            return null;
        }
        return parts[1];
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[SALT_BYTES];
        secureRandom.nextBytes(saltBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
    }

    private String computeSignature(String clientId, String timestamp, String salt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            String payload = clientId + "." + timestamp + "." + salt;
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}
