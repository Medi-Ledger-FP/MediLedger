package com.mediledger.controller;

import com.mediledger.model.AuthResponse;
import com.mediledger.model.LoginRequest;
import com.mediledger.model.RegisterRequest;
import com.mediledger.service.IdentityService;
import com.mediledger.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public AuthController(IdentityService identityService, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.identityService = identityService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user (patient or doctor) with the blockchain network
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            System.out.println("Registering new user: " + request.getUsername() + " with role: " + request.getRole());

            // Check if user already exists
            if (identityService.userExists(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User already exists"));
            }

            // Register user with Fabric CA
            String enrollmentSecret = identityService.registerUser(
                    request.getUsername(),
                    request.getRole());

            // Enroll user and generate X.509 certificate
            identityService.enrollUser(request.getUsername(), enrollmentSecret, request.getRole());

            // Store encrypted password (for later authentication)
            userCredentials.put(
                    request.getUsername(),
                    passwordEncoder.encode(request.getPassword()));

            // Generate JWT token
            String token = jwtService.generateToken(
                    request.getUsername(),
                    request.getRole().name());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .username(request.getUsername())
                    .role(request.getRole())
                    .message("User registered successfully with blockchain identity")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            System.err.println("Error registering user: " + e.getMessage());
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

            // Get user's certificate to extract role (in production, store role with user)
            // For demo, we'll extract from JWT or use a default role
            String role = "PATIENT"; // This should be retrieved from user metadata

            // Generate JWT token
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
}
