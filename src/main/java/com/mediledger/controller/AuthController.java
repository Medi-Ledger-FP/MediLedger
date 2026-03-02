package com.mediledger.controller;

import com.mediledger.model.AuthResponse;
import com.mediledger.model.LoginRequest;
import com.mediledger.model.RegisterRequest;
import com.mediledger.service.IdentityService;
import com.mediledger.service.JwtService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for authentication endpoints
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final IdentityService identityService;
        private final JwtService jwtService;
        private final PasswordEncoder passwordEncoder;

        // In-memory store for demo (replace with database in production)
        private final Map<String, String> userCredentials = new HashMap<>();
        private final Map<String, String> userRoles = new HashMap<>(); // username -> role

        public AuthController(IdentityService identityService, JwtService jwtService, PasswordEncoder passwordEncoder) {
                this.identityService = identityService;
                this.jwtService = jwtService;
                this.passwordEncoder = passwordEncoder;
        }

        /**
         * Seed default accounts on every startup so they survive backend restarts.
         * Credentials (change as needed):
         * admin_user / Admin@123 (ADMIN)
         * doctor_user / Doctor@123 (DOCTOR)
         * patient_user/ Patient@123 (PATIENT)
         */
        @PostConstruct
        public void seedDefaultUsers() {
                // Credentials come from environment variables — never hardcoded.
                // Set these before starting the backend:
                // export SEED_ADMIN_PASS=yourAdminPassword
                // export SEED_DOCTOR_PASS=yourDoctorPassword
                // export SEED_PATIENT_PASS=yourPatientPassword
                record SeedUser(String username, String envVar, String role) {
                }
                java.util.List<SeedUser> seeds = java.util.List.of(
                                new SeedUser("admin_user", "SEED_ADMIN_PASS", "ADMIN"),
                                new SeedUser("doctor_user", "SEED_DOCTOR_PASS", "DOCTOR"),
                                new SeedUser("patient_user", "SEED_PATIENT_PASS", "PATIENT"));
                for (SeedUser s : seeds) {
                        String pass = System.getenv(s.envVar());
                        if (pass == null || pass.isBlank()) {
                                System.out.println("⚠️  Skipping seed for " + s.username() + " — " + s.envVar()
                                                + " not set");
                                continue;
                        }
                        // 1. Store credentials in memory
                        userCredentials.put(s.username(), passwordEncoder.encode(pass));
                        userRoles.put(s.username(), s.role());
                        // 2. Create wallet directory so identityService.userExists() returns true
                        try {
                                java.nio.file.Path userWallet = identityService.getWalletPath().resolve(s.username());
                                java.nio.file.Files.createDirectories(userWallet);
                                java.nio.file.Path meta = userWallet.resolve("metadata.json");
                                if (!java.nio.file.Files.exists(meta)) {
                                        java.nio.file.Files.writeString(meta,
                                                        String.format("{\"mspId\":\"HealthcareOrgMSP\",\"role\":\"%s\",\"mode\":\"jwt-only\"}",
                                                                        s.role()),
                                                        java.nio.file.StandardOpenOption.CREATE);
                                }
                        } catch (Exception e) {
                                System.err.println("⚠️  Wallet dir creation failed for " + s.username() + ": "
                                                + e.getMessage());
                        }
                        System.out.println("🌱 Seeded user: " + s.username() + " [" + s.role() + "]");
                }
        }

        /**
         * Register a new user.
         * PATIENT: open to anyone (self-registration).
         * DOCTOR / ADMIN: requires the caller to already be authenticated as ADMIN.
         */
        @PostMapping("/register")
        public ResponseEntity<?> register(@RequestBody RegisterRequest request,
                        Authentication callerAuth) {
                try {
                        System.out.println("Registering new user: " + request.getUsername()
                                        + " with role: " + request.getRole());

                        // ── Role provisioning security gate ──────────────────────────────────
                        // Only an authenticated ADMIN may create DOCTOR or ADMIN accounts.
                        if (request.getRole() != null &&
                                        (request.getRole() == com.mediledger.model.UserRole.ADMIN
                                                        || request.getRole() == com.mediledger.model.UserRole.DOCTOR)) {
                                boolean callerIsAdmin = callerAuth != null &&
                                                callerAuth.getAuthorities().stream()
                                                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                                if (!callerIsAdmin) {
                                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                        .body(Map.of("error",
                                                                        "Only an Admin can provision DOCTOR or ADMIN accounts. "
                                                                                        + "Register as PATIENT, or ask your system administrator."));
                                }
                        }

                        // Check if user already exists
                        if (userCredentials.containsKey(request.getUsername())) {
                                return ResponseEntity.badRequest()
                                                .body(Map.of("error", "User already exists"));
                        }

                        // ── Attempt Fabric CA registration (X.509 certificates) ──────────────
                        try {
                                if (!identityService.userExists(request.getUsername())) {
                                        String enrollmentSecret = identityService.registerUser(
                                                        request.getUsername(), request.getRole());
                                        identityService.enrollUser(request.getUsername(),
                                                        enrollmentSecret, request.getRole());
                                        System.out.println("✅ Fabric CA enrollment complete for: "
                                                        + request.getUsername());
                                }
                        } catch (Exception caEx) {
                                // CA unavailable — JWT-only mode: create minimal wallet dir so login works
                                System.err.println("⚠️  Fabric CA unavailable, JWT-only mode: " + caEx.getMessage());
                                try {
                                        java.nio.file.Path userPath = identityService.getWalletPath()
                                                        .resolve(request.getUsername());
                                        java.nio.file.Files.createDirectories(userPath);
                                        java.nio.file.Files.writeString(
                                                        userPath.resolve("metadata.json"),
                                                        String.format("{\"mspId\":\"HealthcareOrgMSP\",\"role\":\"%s\",\"mode\":\"jwt-only\"}",
                                                                        request.getRole().name()),
                                                        java.nio.file.StandardOpenOption.CREATE,
                                                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                                } catch (Exception walletEx) {
                                        System.err.println("⚠️  Wallet dir creation failed: " + walletEx.getMessage());
                                }
                        }

                        // ── Always store credentials + issue JWT ──────────────────────────────
                        userCredentials.put(request.getUsername(),
                                        passwordEncoder.encode(request.getPassword()));
                        userRoles.put(request.getUsername(), request.getRole().name());

                        String token = jwtService.generateToken(
                                        request.getUsername(), request.getRole().name());

                        AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .username(request.getUsername())
                                        .role(request.getRole())
                                        .message("User registered successfully")
                                        .build();

                        return ResponseEntity.status(HttpStatus.CREATED).body(response);

                } catch (Exception e) {
                        System.err.println("Error registering user: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Registration failed: " + e.getMessage()));
                }
        }

        /**
         * Bootstrap endpoint to create the FIRST admin account without a token.
         * In a real production system, this is done via a database migration script,
         * not an open API, but it's required for local development and testing.
         */
        @PostMapping("/register-initial-admin")
        public ResponseEntity<?> registerInitialAdmin(@RequestBody RegisterRequest request) {
                try {
                        System.out.println("⚠️ Bootstrapping INITIAL root admin: " + request.getUsername());

                        // Check if user already exists
                        if (userCredentials.containsKey(request.getUsername())) {
                                return ResponseEntity.badRequest().body(Map.of("error", "Admin already exists"));
                        }

                        // Attempt Fabric CA registration for the root admin
                        try {
                                if (!identityService.userExists(request.getUsername())) {
                                        String enrollmentSecret = identityService.registerUser(request.getUsername(),
                                                        request.getRole());
                                        identityService.enrollUser(request.getUsername(), enrollmentSecret,
                                                        request.getRole());
                                        System.out.println("✅ Fabric CA enrollment complete for root admin: "
                                                        + request.getUsername());
                                }
                        } catch (Exception caEx) {
                                // CA unavailable — JWT-only mode: create minimal wallet dir so login works
                                System.err.println("⚠️  Fabric CA unavailable, JWT-only mode for Admin: "
                                                + caEx.getMessage());
                                try {
                                        java.nio.file.Path userPath = identityService.getWalletPath()
                                                        .resolve(request.getUsername());
                                        java.nio.file.Files.createDirectories(userPath);
                                        java.nio.file.Files.writeString(
                                                        userPath.resolve("metadata.json"),
                                                        String.format("{\"mspId\":\"HealthcareOrgMSP\",\"role\":\"%s\",\"mode\":\"jwt-only\"}",
                                                                        request.getRole().name()),
                                                        java.nio.file.StandardOpenOption.CREATE,
                                                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                                } catch (Exception walletEx) {
                                        System.err.println("⚠️  Wallet dir creation failed: " + walletEx.getMessage());
                                }
                        }

                        // Always store credentials + issue JWT
                        userCredentials.put(request.getUsername(), passwordEncoder.encode(request.getPassword()));
                        userRoles.put(request.getUsername(), "ADMIN");

                        String token = jwtService.generateToken(request.getUsername(), "ADMIN");

                        AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .username(request.getUsername())
                                        .role(com.mediledger.model.UserRole.ADMIN)
                                        .message("Root Admin registered successfully")
                                        .build();

                        return ResponseEntity.status(HttpStatus.CREATED).body(response);

                } catch (Exception e) {
                        System.err.println("Error registering root admin: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Registration failed: " + e.getMessage()));
                }
        }

        /**
         * Authenticate user and return JWT token
         */
        @PostMapping("/login")
        public ResponseEntity<?> login(@RequestBody LoginRequest request) {
                try {
                        System.out.println("Login attempt for user: " + request.getUsername());

                        // Verify user exists
                        if (!identityService.userExists(request.getUsername())) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("error", "Invalid credentials"));
                        }

                        // Verify password
                        String storedPassword = userCredentials.get(request.getUsername());
                        if (storedPassword == null || !passwordEncoder.matches(request.getPassword(), storedPassword)) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("error", "Invalid credentials"));
                        }

                        // Retrieve the role stored at registration time
                        String role = userRoles.getOrDefault(request.getUsername(), "PATIENT");
                        System.out.println("Login successful: " + request.getUsername() + " role=" + role);

                        // Generate JWT token with correct role
                        String token = jwtService.generateToken(request.getUsername(), role);

                        AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .username(request.getUsername())
                                        .role(com.mediledger.model.UserRole.valueOf(role))
                                        .message("Login successful")
                                        .build();

                        return ResponseEntity.ok(response);

                } catch (Exception e) {
                        System.err.println("Error during login: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Login failed: " + e.getMessage()));
                }
        }

        /**
         * Admin-only: provision a new DOCTOR or ADMIN account
         * POST /api/auth/admin/register
         */
        @PostMapping("/admin/register")
        public ResponseEntity<?> adminRegister(@RequestBody RegisterRequest request,
                        Authentication callerAuth) {
                // Reuse the same endpoint logic — the gate is already applied in register()
                return register(request, callerAuth);
        }
}
