package com.mediledger.controller;

import com.mediledger.service.AuditService;
import com.mediledger.service.FabricGatewayService;
import com.mediledger.service.FileService;
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

    public FileController(FileService fileService, AuditService auditService,
            FabricGatewayService fabricGateway) {
        this.fileService = fileService;
        this.auditService = auditService;
        this.fabricGateway = fabricGateway;
    }

    /**
     * Upload encrypted medical file
     * POST /api/files/upload
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") String patientId,
            @RequestParam("recordType") String recordType,
            @RequestParam("department") String department,
            @RequestParam(value = "allowedRoles", required = false) String allowedRoles,
            Authentication auth) {
        String uploaderRole = auth != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                : "PATIENT";
        try {
            FileService.FileUploadResult result = allowedRoles != null && !allowedRoles.isBlank()
                    ? fileService.uploadFile(file, patientId, recordType, department, uploaderRole, allowedRoles)
                    : fileService.uploadFile(file, patientId, recordType, department, uploaderRole);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "File uploaded successfully" + (allowedRoles != null ? " (policy: " + allowedRoles + ")" : ""),
                    result.recordId,
                    result.ipfsCid,
                    result.fileHash));
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
                    // Blockchain unavailable — fall through to ABE check
                    System.err.println("⚠️  hasAccess check failed, using ABE fallback: " + ex.getMessage());
                }
            }

            // ── ABE decryption + IPFS download ────────────────────────────────────
            FileService.FileDownloadResult result = fileService.downloadFile(recordId, role);

            auditService.logAccess(userId, role, "VIEW_RECORD",
                    recordId, "UNKNOWN", "SUCCESS", "HTTP", "File download");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "medical_record_" + recordId + ".dat");
            return new ResponseEntity<>(result.fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            auditService.logAccess(userId, role, "VIEW_RECORD",
                    recordId, "UNKNOWN", "DENIED", "HTTP",
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
            // TODO: Get from blockchain
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
    private record UploadResponse(
            boolean success,
            String message,
            String recordId,
            String ipfsCid,
            String fileHash) {
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
