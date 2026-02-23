package com.mediledger.controller;

import com.mediledger.service.EmergencyAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Emergency Access Controller
 *
 * REST API for the threshold-cryptography emergency access workflow:
 *
 * POST /api/emergency/request – Open an emergency access request
 * POST /api/emergency/approve/{id} – Stakeholder submits their key share
 * GET /api/emergency/request/{id} – Get request status
 * GET /api/emergency/patient/{pid} – List requests for a patient
 * GET /api/emergency/all – Admin: list all requests
 */
@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {

    private final EmergencyAccessService emergencyService;

    public EmergencyController(EmergencyAccessService emergencyService) {
        this.emergencyService = emergencyService;
    }

    /**
     * Open an emergency access request.
     * Any authenticated user (e.g. ER doctor) can initiate.
     *
     * Body: { "patientId": "p1", "reason": "Cardiac arrest", "requestedBy": "Dr.
     * Smith" }
     */
    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> openRequest(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        String requesterId = extractUsername(authHeader);
        String patientId = body.get("patientId");
        String reason = body.get("reason");
        String requestedBy = body.getOrDefault("requestedBy", requesterId);

        if (patientId == null || reason == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "patientId and reason are required"));
        }

        EmergencyAccessService.EmergencyRequest req = emergencyService.openRequest(requesterId, patientId, reason,
                requestedBy);

        return ResponseEntity.ok(Map.of(
                "requestId", req.requestId(),
                "patientId", req.patientId(),
                "status", req.status().name(),
                "threshold", req.threshold(),
                "message", "Emergency request opened. Requires " + req.threshold() + " approvals.",
                "timestamp", req.timestamp().toString()));
    }

    /**
     * Stakeholder approves the emergency request by submitting their share.
     *
     * Body: { "shareIndex": 2 } ← which of the N shares this approver holds
     */
    @PostMapping("/approve/{requestId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<?> approve(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {

        String approverId = extractUsername(authHeader);
        int shareIndex = Integer.parseInt(body.get("shareIndex").toString());

        EmergencyAccessService.ApprovalResult result = emergencyService.approveRequest(requestId, approverId,
                shareIndex);

        if (result.granted()) {
            return ResponseEntity.ok(Map.of(
                    "requestId", result.requestId(),
                    "granted", true,
                    "message", result.message(),
                    "reconstructedKey", result.reconstructedKey(),
                    "warning", "⚠️ This key grants temporary emergency access. All usage is logged."));
        }

        return ResponseEntity.ok(Map.of(
                "requestId", result.requestId(),
                "granted", false,
                "message", result.message()));
    }

    /**
     * Get the status of an emergency request.
     */
    @GetMapping("/request/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRequest(@PathVariable String requestId) {
        EmergencyAccessService.EmergencyRequest req = emergencyService.getRequest(requestId);
        if (req == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "requestId", req.requestId(),
                "patientId", req.patientId(),
                "reason", req.reason(),
                "status", req.status().name(),
                "sharesCollected", req.collectedShares().size(),
                "threshold", req.threshold(),
                "timestamp", req.timestamp().toString()));
    }

    /**
     * List all emergency requests for a patient.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    public ResponseEntity<List<Map<String, Object>>> listForPatient(@PathVariable String patientId) {
        List<Map<String, Object>> result = emergencyService.listRequests(patientId).stream()
                .map(r -> Map.<String, Object>of(
                        "requestId", r.requestId(),
                        "requesterId", r.requesterId(),
                        "reason", r.reason(),
                        "status", r.status().name(),
                        "sharesCollected", r.collectedShares().size(),
                        "threshold", r.threshold()))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Admin: list all emergency requests.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<Map<String, Object>> result = emergencyService.listAllRequests().stream()
                .map(r -> Map.<String, Object>of(
                        "requestId", r.requestId(),
                        "patientId", r.patientId(),
                        "requesterId", r.requesterId(),
                        "reason", r.reason(),
                        "status", r.status().name(),
                        "sharesCollected", r.collectedShares().size(),
                        "threshold", r.threshold(),
                        "timestamp", r.timestamp().toString()))
                .toList();
        return ResponseEntity.ok(result);
    }

    private String extractUsername(String authHeader) {
        // JWT sub claim extraction (simplified — real impl uses JwtService)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String payload = authHeader.split("\\.")[1];
                String decoded = new String(java.util.Base64.getDecoder().decode(payload));
                return decoded.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
            } catch (Exception e) {
                /* fall through */ }
        }
        return "anonymous";
    }
}
