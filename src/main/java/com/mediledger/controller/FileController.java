package com.mediledger.controller;

import com.mediledger.service.AuditService;
import com.mediledger.service.ConsentService;
import com.mediledger.service.FabricGatewayService;
import com.mediledger.service.FileService;
import com.mediledger.service.RecordService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final AuditService auditService;
    private final FabricGatewayService fabricGateway;
    private final ConsentService consentService;
    private final RecordService recordService;

    public FileController(FileService fileService, AuditService auditService,
            FabricGatewayService fabricGateway,
            ConsentService consentService,
            RecordService recordService) {
        this.fileService = fileService;
        this.auditService = auditService;
        this.fabricGateway = fabricGateway;
        this.consentService = consentService;
        this.recordService = recordService;
    }

    /**
     * Upload encrypted medical file
     * POST /api/files/upload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") String patientId,
            @RequestParam("recordType") String recordType,
            @RequestParam("department") String department,
            @RequestParam(value = "allowedRoles", required = false) String allowedRoles,
            Authentication auth) {
        try {
            System.out.println("🚨 DEBUG UPLOAD 🚨");
            System.out.println("   Original Filename: " + file.getOriginalFilename());
            System.out.println("   recordType Param: " + recordType);

            String uploaderRole = auth != null && !auth.getAuthorities().isEmpty()
                    ? auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                    : "UNKNOWN";
            FileService.FileUploadResult result = allowedRoles != null && !allowedRoles.isBlank()
                    ? fileService.uploadFile(file, patientId, recordType, department, uploaderRole, allowedRoles)
                    : fileService.uploadFile(file, patientId, recordType, department, uploaderRole);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "File uploaded successfully" + (allowedRoles != null ? " (policy: " + allowedRoles + ")" : ""),
                    result.recordId,
                    result.ipfsCid,
                    result.fileHash,
                    result.abePolicy,
                    result.sssShares));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Upload failed: " + e.getMessage()));
        }
    }

    /**
     * Download and decrypt medical file
     * GET /api/files/download/{recordId}
     */
    @GetMapping("/download/{recordId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> downloadFile(@PathVariable String recordId,
            Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        String role = auth != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                : "UNKNOWN";
        try {
            // ── Smart contract hasAccess() check ──────────────────────────────────
            if (fabricGateway.isAvailable()) {
                try {
                    String byRole = fabricGateway.evaluateTransaction("hasAccess", role, recordId);
                    String byUser = fabricGateway.evaluateTransaction("hasAccess", userId, recordId);
                    if (!"true".equalsIgnoreCase(byRole.trim())
                            && !"true".equalsIgnoreCase(byUser.trim())) {
                        auditService.logAccess(userId, role, "VIEW_RECORD",
                                recordId, "UNKNOWN", "DENIED", "HTTP",
                                "Smart contract hasAccess() denied");
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(new ErrorResponse("Smart contract: access denied for "
                                        + role + " / " + userId));
                    }
                } catch (Exception ex) {
                    // Blockchain unavailable — fall through to consent + ABE check
                    System.err.println("⚠️  hasAccess check failed, using consent+ABE fallback: " + ex.getMessage());
                }
            }

            // ── Consent gate (DOCTOR only) ────────────────────────────────────────
            // PATIENT downloads their own files, ADMIN has master key via ABE.
            // DOCTOR must have explicit patient consent per record.
            if ("DOCTOR".equals(role)) {
                String patientId = recordService.getPatientIdForRecord(recordId);
                if (patientId == null || patientId.trim().isEmpty()) {
                    auditService.logAccess(userId, role, "VIEW_RECORD",
                            recordId, "UNKNOWN", "DENIED", "HTTP",
                            "Cannot resolve patient owner for record " + recordId + " to verify consent.");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(new ErrorResponse(
                                    "Access denied: Cannot verify consent because the patient owner of this record is unknown."));
                }

                boolean hasConsent = consentService.checkAccess(userId, recordId, patientId);
                if (!hasConsent) {
                    auditService.logAccess(userId, role, "VIEW_RECORD",
                            recordId, patientId, "DENIED", "HTTP",
                            "No patient consent for doctor " + userId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(new ErrorResponse(
                                    "Access denied: Patient has not granted you consent for this record. "
                                            + "Ask the patient to go to their dashboard and grant access "
                                            + "to your username (" + userId + ")."));
                }
            }

            // ── ABE decryption + IPFS download ────────────────────────────────────
            FileService.FileDownloadResult result = fileService.downloadFile(recordId, role);

            String patientId = recordService.getPatientIdForRecord(recordId);
            auditService.logAccess(userId, role, "VIEW_RECORD",
                    recordId, patientId != null ? patientId : "UNKNOWN", "SUCCESS", "HTTP", "File download");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            String extension = ".dat";
            if (result.recordType != null && result.recordType.contains("|")) {
                extension = result.recordType.substring(result.recordType.lastIndexOf("|") + 1);
            }
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"medical_record_" + recordId + extension + "\"");
            return new ResponseEntity<>(result.fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            String patientId = recordService.getPatientIdForRecord(recordId);
            auditService.logAccess(userId, role, "VIEW_RECORD",
                    recordId, patientId != null ? patientId : "UNKNOWN", "DENIED", "HTTP",
                    "Download failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Download failed: " + e.getMessage()));
        }
    }

    /**
     * Get file metadata
     * GET /api/files/metadata/{recordId}
     */
    @GetMapping("/metadata/{recordId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getFileMetadata(@PathVariable String recordId) {
        try {
            return ResponseEntity.ok(new MetadataResponse(
                    recordId,
                    "QmSampleCID",
                    "sample_hash",
                    "Pending blockchain integration"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Failed to get metadata: " + e.getMessage()));
        }
    }

    /**
     * Delete file
     * DELETE /api/files/{recordId}
     */
    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFile(@PathVariable String recordId) {
        try {
            fileService.deleteFile(recordId);
            return ResponseEntity.ok(new SuccessResponse("File deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Delete failed: " + e.getMessage()));
        }
    }

    // Response classes
    public static class UploadResponse {
        public boolean success;
        public String message;
        public String recordId;
        public String ipfsCid;
        public String fileHash;
        public String abePolicy;
        public int sssShares;

        public UploadResponse(boolean success, String message, String recordId, String ipfsCid, String fileHash,
                String abePolicy, int sssShares) {
            this.success = success;
            this.message = message;
            this.recordId = recordId;
            this.ipfsCid = ipfsCid;
            this.fileHash = fileHash;
            this.abePolicy = abePolicy;
            this.sssShares = sssShares;
        }
    }

    private record ErrorResponse(String error) {
    }

    private record SuccessResponse(String message) {
    }

    private record MetadataResponse(
            String recordId,
            String ipfsCid,
            String fileHash,
            String status) {
    }
}
