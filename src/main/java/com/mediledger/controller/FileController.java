package com.mediledger.controller;

import com.mediledger.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * File Controller
 * Handles encrypted file upload/download via IPFS + blockchain
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
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
            @RequestParam("department") String department) {
        try {
            FileService.FileUploadResult result = fileService.uploadFile(
                    file, patientId, recordType, department);

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    "File uploaded successfully",
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
    public ResponseEntity<?> downloadFile(@PathVariable String recordId) {
        try {
            FileService.FileDownloadResult result = fileService.downloadFile(recordId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "medical_record_" + recordId + ".pdf");

            return new ResponseEntity<>(result.fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
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
