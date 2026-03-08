package com.mediledger.controller;

import com.mediledger.service.EmergencyAccessService;
import com.mediledger.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {

        private final EmergencyAccessService emergencyService;
        private final FileService fileService;

        public EmergencyController(EmergencyAccessService emergencyService, FileService fileService) {
                this.emergencyService = emergencyService;
                this.fileService = fileService;
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
                String recordId = body.getOrDefault("recordId", "UNKNOWN"); // which record to access
                String reason = body.get("reason");
                String requestedBy = body.getOrDefault("requestedBy", requesterId);

                if (patientId == null || reason == null) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("error", "patientId and reason are required"));
                }

                EmergencyAccessService.EmergencyRequest req = emergencyService.openRequest(
                                requesterId, patientId, recordId, reason, requestedBy);

                return ResponseEntity.ok(Map.of(
                                "requestId", req.requestId(),
                                "patientId", req.patientId(),
                                "recordId", req.recordId(),
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
        @PreAuthorize("isAuthenticated()")
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
                                        "warning",
                                        "⚠️ This key grants temporary emergency access. All usage is logged."));
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
        @GetMapping("/requests")
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

        /**
         * Emergency file download — uses the SSS-reconstructed AES key to decrypt.
         * Only available once the emergency request is APPROVED (threshold met).
         *
         * GET /api/emergency/download/{requestId}
         */
        @GetMapping("/download/{requestId}")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<?> emergencyDownload(@PathVariable String requestId) {
                EmergencyAccessService.GrantedAccess access = emergencyService.getGrantedAccess(requestId);
                if (access == null) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(Map.of("error",
                                                        "Emergency access not granted for request " + requestId +
                                                                        ". Reach threshold approvals first."));
                }
                try {
                        FileService.FileDownloadResult result = fileService.downloadWithEmergencyKey(
                                        access.recordId(), access.aesKeyHex());

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

                        String extension = ".dat";
                        if (result.recordType != null && result.recordType.contains("|")) {
                                extension = result.recordType.substring(result.recordType.lastIndexOf("|") + 1);
                        }
                        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"emergency_record_" + access.recordId() + extension
                                                        + "\"");
                        return new ResponseEntity<>(result.fileBytes, headers, HttpStatus.OK);
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Emergency download failed: " + e.getMessage()));
                }
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
