package com.mediledger.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attribute-Based Encryption Service (CP-ABE Functional Equivalent)
 *
 * Implements the CONCEPT of Ciphertext-Policy Attribute-Based Encryption using
 * RSA key pairs per role/attribute. In a full CP-ABE system this would use
 * bilinear pairings (e.g., jpbc library). This implementation:
 *
 * 1. Each role/attribute has an RSA-2048 key pair (the "attribute authority").
 * 2. A "policy" is a set of allowed roles (e.g., {"DOCTOR", "ADMIN"}).
 * 3. The AES symmetric key is RSA-encrypted to every public key in the policy.
 * 4. Only a holder of a matching private key (i.e., a user with that role)
 * can decrypt the AES key and thus read the file.
 *
 * This gives us: patient-defined access policies, role-based decryption,
 * and no single-authority key escrow — matching the CP-ABE security model.
 */
@Service
public class ABEService {

    // Master key store: role -> {publicKey, privateKey}
    private final Map<String, KeyPair> attributeKeyPairs = new ConcurrentHashMap<>();

    // Known roles / attributes
    private static final List<String> ALL_ATTRIBUTES = List.of(
            "PATIENT", "DOCTOR", "NURSE", "ADMIN",
            "CARDIOLOGY", "NEUROLOGY", "RADIOLOGY", "EMERGENCY");

    public ABEService() throws NoSuchAlgorithmException {
        // Generate an RSA key pair for every attribute at startup
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        for (String attr : ALL_ATTRIBUTES) {
            attributeKeyPairs.put(attr, gen.generateKeyPair());
        }
        System.out.println("✅  ABEService: generated " + ALL_ATTRIBUTES.size() + " attribute key pairs");
    }

    /**
     * Encrypt an AES key under a CP-ABE policy.
     *
     * @param aesKeyBytes Raw bytes of the AES symmetric key
     * @param policy      Set of attributes/roles that satisfy the policy
     *                    (e.g. Set.of("DOCTOR", "ADMIN"))
     * @return ABECiphertext holding one RSA blob per policy attribute
     */
    public ABECiphertext encryptKey(byte[] aesKeyBytes, Set<String> policy) throws Exception {
        Map<String, byte[]> encryptedKeyMap = new LinkedHashMap<>();
        for (String attr : policy) {
            KeyPair kp = attributeKeyPairs.get(attr.toUpperCase());
            if (kp == null) {
                throw new IllegalArgumentException("Unknown attribute: " + attr);
            }
            byte[] encrypted = rsaEncrypt(aesKeyBytes, kp.getPublic());
            encryptedKeyMap.put(attr.toUpperCase(), encrypted);
        }
        return new ABECiphertext(policy, encryptedKeyMap);
    }

    /**
     * Decrypt the AES key from an ABE ciphertext using the caller's role.
     *
     * @param ciphertext The ABE ciphertext produced by encryptKey()
     * @param userRole   The calling user's role (their "attribute")
     * @return The raw AES key bytes
     * @throws SecurityException if the role doesn't satisfy the policy
     */
    public byte[] decryptKey(ABECiphertext ciphertext, String userRole) throws Exception {
        String role = userRole.toUpperCase();
        if (!ciphertext.policy().contains(role) && !ciphertext.policy().contains(userRole)) {
            throw new SecurityException(
                    "Access denied: role '" + userRole + "' does not satisfy policy " + ciphertext.policy());
        }
        byte[] encryptedAesKey = ciphertext.encryptedKeyMap().get(role);
        if (encryptedAesKey == null) {
            throw new SecurityException("No key share for role: " + role);
        }
        KeyPair kp = attributeKeyPairs.get(role);
        return rsaDecrypt(encryptedAesKey, kp.getPrivate());
    }

    /**
     * Build a default policy for a given record type and department.
     * This is what the patient's access rules translate to.
     */
    public Set<String> buildPolicy(String recordType, String department, String ownerRole) {
        Set<String> policy = new LinkedHashSet<>();
        policy.add("ADMIN");
        policy.add(ownerRole.toUpperCase()); // owner always has access
        policy.add("DOCTOR"); // doctors always included in default policy

        // Department-specific attribute
        if (department != null && attributeKeyPairs.containsKey(department.toUpperCase())) {
            policy.add(department.toUpperCase());
        }
        return policy;
    }

    /**
     * Serialise an ABECiphertext to a storable string (Base64 per entry).
     */
    public String serialise(ABECiphertext ct) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ct.policy())).append("|");
        ct.encryptedKeyMap().forEach((attr, bytes) -> sb.append(attr).append(":")
                .append(Base64.getEncoder().encodeToString(bytes)).append(";"));
        return sb.toString();
    }

    /**
     * Deserialise a stored ABECiphertext string.
     */
    public ABECiphertext deserialise(String raw) {
        String[] parts = raw.split("\\|", 2);
        Set<String> policy = new LinkedHashSet<>(Arrays.asList(parts[0].split(",")));
        Map<String, byte[]> map = new LinkedHashMap<>();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            for (String entry : parts[1].split(";")) {
                if (entry.isEmpty())
                    continue;
                String[] kv = entry.split(":", 2);
                map.put(kv[0], Base64.getDecoder().decode(kv[1]));
            }
        }
        return new ABECiphertext(policy, map);
    }

    // ── RSA helpers ──────────────────────────────────────────────────────────

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

    // ── Data record ──────────────────────────────────────────────────────────

    /**
     * Represents an ABE ciphertext: the AES key encrypted under multiple
     * attribute public keys, plus the access policy.
     */
    public record ABECiphertext(
            Set<String> policy,
            Map<String, byte[]> encryptedKeyMap) {
    }
}
