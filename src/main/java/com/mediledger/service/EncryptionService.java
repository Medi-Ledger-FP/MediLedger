package com.mediledger.service;

import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption Service for AES-256-GCM file encryption
 * Handles secure encryption/decryption of medical records
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int AES_KEY_SIZE = 256; // bits

    // TODO: In production, manage keys securely (HSM, AWS KMS, Azure Key Vault)
    private SecretKey masterKey;

    public EncryptionService() throws NoSuchAlgorithmException {
        // Generate master key (in production, load from secure storage)
        this.masterKey = generateKey();
    }

    /**
     * Generate AES-256 key
     */
    private SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    /**
     * Encrypt file data
     *
     * @param plaintext File bytes to encrypt
     * @return Encrypted data with IV prepended
     */
    public byte[] encrypt(byte[] plaintext) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV to ciphertext (IV + ciphertext)
        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

        return encrypted;
    }

    /**
     * Decrypt file data
     *
     * @param encrypted Encrypted data with IV prepended
     * @return Decrypted plaintext
     */
    public byte[] decrypt(byte[] encrypted) throws Exception {
        // Extract IV (first 12 bytes)
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);

        // Extract ciphertext (remaining bytes)
        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
        System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

        // Decrypt
        return cipher.doFinal(ciphertext);
    }

    /**
     * Calculate SHA-256 hash of data (for integrity verification)
     */
    public String calculateHash(byte[] data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Get master key as Base64 string (for backup/recovery)
     */
    public String exportKey() {
        return Base64.getEncoder().encodeToString(masterKey.getEncoded());
    }

    /**
     * Import master key from Base64 string
     */
    public void importKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Get the raw bytes of the current master AES key.
     * Used by ABEService to encrypt the key under a CP-ABE policy,
     * and by ShamirSecretSharingService to split it for emergency access.
     */
    public byte[] getLastKey() {
        return masterKey.getEncoded();
    }

    /**
     * Decrypt data using an externally provided AES key (recovered via CP-ABE).
     * Called by FileService after the role-check and key decryption step.
     *
     * @param encrypted   IV-prepended ciphertext
     * @param rawKeyBytes 32-byte AES-256 key
     */
    public byte[] decryptWithKey(byte[] encrypted, byte[] rawKeyBytes) throws Exception {
        SecretKey key = new SecretKeySpec(rawKeyBytes, "AES");

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }
}
