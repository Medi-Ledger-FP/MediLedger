package com.mediledger.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attribute-Based Encryption Service (CP-ABE Functional Equivalent)
 *
 * Uses RSA-2048 key pairs per role/attribute.
 * Keys are PERSISTED to data/abe-keys/ as DER files so that ABE
 * ciphertext encrypted in one server session can be decrypted in
 * any future session — fixes the critical ephemeral-key bug.
 */
@Service
public class ABEService {

    private static final String KEYS_DIR = "data/abe-keys";

    private final Map<String, KeyPair> attributeKeyPairs = new ConcurrentHashMap<>();

    private static final List<String> ALL_ATTRIBUTES = List.of(
            "PATIENT", "DOCTOR", "NURSE", "ADMIN",
            "CARDIOLOGY", "NEUROLOGY", "RADIOLOGY", "EMERGENCY");

    public ABEService() throws Exception {
        Path dir = Paths.get(KEYS_DIR);
        Files.createDirectories(dir);

        boolean anyMissing = ALL_ATTRIBUTES.stream()
                .anyMatch(a -> !Files.exists(dir.resolve(a + ".priv")) ||
                        !Files.exists(dir.resolve(a + ".pub")));

        if (anyMissing) {
            generateAndSaveKeys(dir);
            System.out.println("✅  ABEService: generated " + ALL_ATTRIBUTES.size()
                    + " RSA key pairs → persisted to " + KEYS_DIR);
        } else {
            loadKeysFromDisk(dir);
            System.out.println("✅  ABEService: loaded " + attributeKeyPairs.size()
                    + " RSA key pairs from " + KEYS_DIR + " (cross-restart decryption OK)");
        }
    }

    // ── Key persistence ───────────────────────────────────────────────────────

    private void generateAndSaveKeys(Path dir) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        for (String attr : ALL_ATTRIBUTES) {
            KeyPair kp = gen.generateKeyPair();
            attributeKeyPairs.put(attr, kp);
            Files.write(dir.resolve(attr + ".priv"), kp.getPrivate().getEncoded());
            Files.write(dir.resolve(attr + ".pub"), kp.getPublic().getEncoded());
        }
    }

    private void loadKeysFromDisk(Path dir) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        for (String attr : ALL_ATTRIBUTES) {
            byte[] privBytes = Files.readAllBytes(dir.resolve(attr + ".priv"));
            byte[] pubBytes = Files.readAllBytes(dir.resolve(attr + ".pub"));
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            attributeKeyPairs.put(attr, new KeyPair(pub, priv));
        }
    }

    // ── Encrypt / Decrypt ─────────────────────────────────────────────────────

    /**
     * Encrypt an AES key under a CP-ABE policy (set of allowed roles).
     */
    public ABECiphertext encryptKey(byte[] aesKeyBytes, Set<String> policy) throws Exception {
        Map<String, byte[]> encryptedKeyMap = new LinkedHashMap<>();
        for (String attr : policy) {
            KeyPair kp = attributeKeyPairs.get(attr.toUpperCase());
            if (kp == null)
                continue;
            encryptedKeyMap.put(attr.toUpperCase(), rsaEncrypt(aesKeyBytes, kp.getPublic()));
        }
        return new ABECiphertext(policy, encryptedKeyMap);
    }

    /**
     * Decrypt the AES key — user must possess a role that satisfies the policy.
     */
    public byte[] decryptKey(ABECiphertext ciphertext, String userRole) throws Exception {
        String role = userRole.toUpperCase();
        if (!ciphertext.policy().contains(role)) {
            throw new SecurityException(
                    "Access denied: role '" + userRole + "' does not satisfy policy " + ciphertext.policy());
        }
        byte[] encryptedAesKey = ciphertext.encryptedKeyMap().get(role);
        if (encryptedAesKey == null)
            throw new SecurityException("No key share for role: " + role);
        return rsaDecrypt(encryptedAesKey, attributeKeyPairs.get(role).getPrivate());
    }

    /**
     * Build a default access policy for the given record + uploader role.
     * Patient and doctor always get access; admin always gets access.
     */
    public Set<String> buildPolicy(String recordType, String department, String ownerRole) {
        Set<String> policy = new LinkedHashSet<>();
        policy.add("ADMIN");
        policy.add(ownerRole.toUpperCase());
        policy.add("DOCTOR");
        if (department != null && attributeKeyPairs.containsKey(department.toUpperCase())) {
            policy.add(department.toUpperCase());
        }
        return policy;
    }

    /**
     * Build a patient-defined access policy from a comma-separated role list.
     * Always adds ADMIN for administrative access.
     * If the list is empty or null, falls back to the default policy.
     */
    public Set<String> buildPolicy(String recordType, String department,
            String ownerRole, String customAllowedRoles) {
        if (customAllowedRoles == null || customAllowedRoles.isBlank()) {
            return buildPolicy(recordType, department, ownerRole);
        }
        Set<String> policy = new LinkedHashSet<>();
        policy.add("ADMIN"); // Always include admin
        for (String role : customAllowedRoles.split(",")) {
            String r = role.trim().toUpperCase();
            if (!r.isEmpty())
                policy.add(r);
        }
        return policy;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    public String serialise(ABECiphertext ct) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ct.policy())).append("|");
        ct.encryptedKeyMap().forEach((attr, bytes) -> sb.append(attr).append(":")
                .append(Base64.getEncoder().encodeToString(bytes)).append(";"));
        return sb.toString();
    }

    public ABECiphertext deserialise(String raw) {
        String[] parts = raw.split("\\|", 2);
        Set<String> policy = new LinkedHashSet<>(Arrays.asList(parts[0].split(",")));
        Map<String, byte[]> map = new LinkedHashMap<>();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            for (String entry : parts[1].split(";")) {
                if (entry.isEmpty())
                    continue;
                String[] kv = entry.split(":", 2);
                if (kv.length == 2)
                    map.put(kv[0], Base64.getDecoder().decode(kv[1]));
            }
        }
        return new ABECiphertext(policy, map);
    }

    // ── RSA helpers ───────────────────────────────────────────────────────────

    private byte[] rsaEncrypt(byte[] data, PublicKey pub) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        return cipher.doFinal(data);
    }

    private byte[] rsaDecrypt(byte[] data, PrivateKey priv) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, priv);
        return cipher.doFinal(data);
    }

    public record ABECiphertext(
            Set<String> policy,
            Map<String, byte[]> encryptedKeyMap) {
    }
}
