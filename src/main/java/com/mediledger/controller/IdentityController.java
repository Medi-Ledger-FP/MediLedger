package com.mediledger.controller;

import com.mediledger.service.IdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for blockchain identity management
 */
@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * Enroll admin identity (one-time bootstrap operation)
     * This endpoint should be called once during initial setup
     */
    @PostMapping("/enroll-admin")
    public ResponseEntity<?> enrollAdmin() {
        try {
            System.out.println("Enrolling admin identity");
            identityService.enrollAdmin();

            return ResponseEntity.ok(Map.of(
                    "message", "Admin enrolled successfully",
                    "status", "success"));
        } catch (Exception e) {
            System.err.println("Error enrolling admin: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Admin enrollment failed: " + e.getMessage()));
        }
    }

    /**
     * Get user's X.509 certificate
     * Protected endpoint - requires authentication
     */
    @GetMapping("/certificate/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCertificate(@PathVariable String username) {
        try {
            System.out.println("Fetching certificate for user: " + username);

            String certificate = identityService.getUserCertificate(username);

            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "certificate", certificate,
                    "format", "X.509 PEM"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found: " + username));
        } catch (Exception e) {
            System.err.println("Error fetching certificate: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve certificate: " + e.getMessage()));
        }
    }

    /**
     * Check if user exists in the blockchain wallet
     */
    @GetMapping("/exists/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> checkUserExists(@PathVariable String username) {
        try {
            boolean exists = identityService.userExists(username);

            return ResponseEntity.ok(Map.of(
                    "username", username,
                    "exists", exists));
        } catch (Exception e) {
            System.err.println("Error checking user existence: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check user: " + e.getMessage()));
        }
    }
}
