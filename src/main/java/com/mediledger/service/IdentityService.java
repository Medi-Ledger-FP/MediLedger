package com.mediledger.service;

import com.mediledger.config.FabricConfig;
import com.mediledger.model.UserRole;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Set;

/**
 * Service for managing blockchain identities using Hyperledger Fabric CA
 * Handles enrollment and registration of patients and doctors with X.509
 * certificates
 */
@Service
public class IdentityService {

    private final HFCAClient caClient;
    private final Path walletPath;
    private final FabricConfig fabricConfig;

    @Value("${fabric.ca.admin.username}")
    private String adminUsername;

    @Value("${fabric.ca.admin.password}")
    private String adminPassword;

    public IdentityService(HFCAClient caClient, Path walletPath, FabricConfig fabricConfig) {
        this.caClient = caClient;
        this.walletPath = walletPath;
        this.fabricConfig = fabricConfig;
    }

    /**
     * Enrolls the admin user with the CA (bootstrap operation)
     * This should be called once during initial setup
     */
    public void enrollAdmin() throws Exception {
        System.out.println("Enrolling admin user...");

        // Check if admin identity already exists
        if (userExists(adminUsername)) {
            System.out.println("Admin identity already exists in wallet");
            return;
        }

        // Enroll admin with the CA
        EnrollmentRequest enrollmentRequest = new EnrollmentRequest();
        enrollmentRequest.addHost("localhost");

        Enrollment enrollment = caClient.enroll(adminUsername, adminPassword, enrollmentRequest);

        // Store identity in wallet
        storeIdentity(adminUsername, enrollment, fabricConfig.getMspId(), "ADMIN");

        System.out.println("Admin enrolled successfully");
    }

    /**
     * Registers a new user (patient or doctor) with the CA
     * Returns the enrollment secret that can be used to enroll the user
     */
    public String registerUser(String username, UserRole role) throws Exception {
        System.out.println("Registering user: " + username + " with role: " + role);

        // Check if user already exists
        if (userExists(username)) {
            throw new IllegalStateException("User already exists in wallet: " + username);
        }

        // Get admin enrollment for registration
        Enrollment adminEnrollment = loadIdentity(adminUsername);
        if (adminEnrollment == null) {
            throw new IllegalStateException("Admin user not found. Please enroll admin first.");
        }

        // Create admin user object for CA client
        User admin = createUser(adminUsername, adminEnrollment, fabricConfig.getMspId());

        // Create registration request
        RegistrationRequest registrationRequest = new RegistrationRequest(username);
        registrationRequest.setAffiliation("org1.department1");
        registrationRequest.setEnrollmentID(username);
        if (role == UserRole.ADMIN) {
            // These are the "Magic Strings" Fabric looks for
            registrationRequest.addAttribute(
                    new org.hyperledger.fabric_ca.sdk.Attribute("hf.Registrar.Roles", "client,user,peer"));
            registrationRequest
                    .addAttribute(new org.hyperledger.fabric_ca.sdk.Attribute("hf.Registrar.Attributes", "*"));
            registrationRequest.addAttribute(new org.hyperledger.fabric_ca.sdk.Attribute("admin", "true", true)); // cert
        } else {
            registrationRequest.addAttribute(new org.hyperledger.fabric_ca.sdk.Attribute("role", role.name()));
        }

        // Register user with CA
        String enrollmentSecret = caClient.register(registrationRequest, admin);

        System.out.println("User " + username + " registered successfully");
        return enrollmentSecret;
    }

    /**
     * Enrolls a user with the CA using their registration secret
     * Generates and stores the X.509 certificate in the wallet
     */
    public Enrollment enrollUser(String username, String secret, UserRole role) throws Exception {
        System.out.println("Enrolling user: " + username);

        // Check if user already enrolled
        if (userExists(username)) {
            System.out.println("User " + username + " already enrolled");
            return loadIdentity(username);
        }

        // Enroll user with CA
        EnrollmentRequest enrollmentRequest = new EnrollmentRequest();
        Enrollment enrollment = caClient.enroll(username, secret, enrollmentRequest);

        // Store identity in wallet
        storeIdentity(username, enrollment, fabricConfig.getMspId(), role.name());

        System.out.println("User " + username + " enrolled successfully");
        return enrollment;
    }

    /**
     * Retrieves a user's certificate from the wallet
     */
    public String getUserCertificate(String username) throws Exception {
        Enrollment enrollment = loadIdentity(username);
        if (enrollment == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        return enrollment.getCert();
    }

    /**
     * Checks if a user exists in the wallet
     */
    public boolean userExists(String username) throws Exception {
        Path userPath = walletPath.resolve(username);
        return Files.exists(userPath);
    }

    /**
     * Stores identity in wallet directory
     */
    private void storeIdentity(String username, Enrollment enrollment, String mspId, String role) throws IOException {
        Path userPath = walletPath.resolve(username);
        Files.createDirectories(userPath);

        // Store certificate
        Path certPath = userPath.resolve("cert.pem");
        Files.writeString(certPath, enrollment.getCert());

        // Store private key
        Path keyPath = userPath.resolve("key.pem");
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(enrollment.getKey());
        }
        Files.writeString(keyPath, stringWriter.toString());

        // Store metadata
        Path metadataPath = userPath.resolve("metadata.json");
        String metadata = String.format("{\"mspId\":\"%s\",\"role\":\"%s\"}", mspId, role);
        Files.writeString(metadataPath, metadata);
    }

    /**
     * Loads identity from wallet directory
     */
    private Enrollment loadIdentity(String username) throws IOException {
        Path userPath = walletPath.resolve(username);
        if (!Files.exists(userPath)) {
            return null;
        }

        Path certPath = userPath.resolve("cert.pem");
        Path keyPath = userPath.resolve("key.pem");

        if (!Files.exists(certPath) || !Files.exists(keyPath)) {
            return null;
        }

        String cert = Files.readString(certPath);
        final String keyPem = Files.readString(keyPath);

        return new Enrollment() {
            @Override
            public PrivateKey getKey() {
                try {
                    return parsePrivateKey(keyPem);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse private key", e);
                }
            }

            @Override
            public String getCert() {
                return cert;
            }
        };
    }

    /**
     * Helper method to create a User object
     */
    private User createUser(String name, Enrollment enrollment, String mspId) {
        return new User() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Set<String> getRoles() {
                return null;
            }

            @Override
            public String getAccount() {
                return null;
            }

            @Override
            public String getAffiliation() {
                return "org1.department1";
            }

            @Override
            public Enrollment getEnrollment() {
                return enrollment;
            }

            @Override
            public String getMspId() {
                return mspId;
            }
        };
    }

    /**
     * Parse private key from PEM string
     */
    private PrivateKey parsePrivateKey(String keyPem) throws Exception {
        org.bouncycastle.openssl.PEMParser pemParser = new org.bouncycastle.openssl.PEMParser(
                new StringReader(keyPem));
        Object object = pemParser.readObject();
        pemParser.close();

        org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();

        if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
            return converter.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) object).getPrivate();
        } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
            return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
        }

        throw new Exception("Unable to parse private key");
    }
}
