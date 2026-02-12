package com.mediledger.controller;

import com.mediledger.dto.RecordDTO;
import com.mediledger.dto.RecordDTO.CreateRecordRequest;
import com.mediledger.dto.RecordDTO.RecordResponse;
import com.mediledger.service.RecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Medical Record operations
 * Exposes blockchain chaincode functions via HTTP
 */
@RestController
@RequestMapping("/api/records")
@CrossOrigin(origins = "*")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * Create a new medical record
     * POST /api/records
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<RecordResponse> createRecord(@RequestBody CreateRecordRequest request) {
        try {
            String recordId = recordService.createRecord(
                    request.getPatientId(),
                    request.getIpfsCid(),
                    request.getFileHash(),
                    request.getRecordType(),
                    request.getDepartment());

            RecordResponse response = new RecordResponse(
                    true,
                    "Record created successfully: " + recordId,
                    null // TODO: Parse and return RecordDTO from result
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            RecordResponse response = new RecordResponse(
                    false,
                    "Failed to create record: " + e.getMessage(),
                    null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get a medical record by ID
     * GET /api/records/{recordId}
     */
    @GetMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<RecordResponse> getRecord(@PathVariable String recordId) {
        try {
            String result = recordService.getRecord(recordId);

            // TODO: Parse JSON result into RecordDTO
            RecordResponse response = new RecordResponse(
                    true,
                    "Record retrieved successfully",
                    null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            RecordResponse response = new RecordResponse(
                    false,
                    "Failed to get record: " + e.getMessage(),
                    null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get all records for a patient
     * GET /api/records/patient/{patientId}
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<?> getPatientRecords(@PathVariable String patientId) {
        try {
            String result = recordService.getRecordsByPatient(patientId);

            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to get records: " + e.getMessage());
        }
    }

    /**
     * Update record IPFS CID (after re-encryption)
     * PUT /api/records/{recordId}/cid
     */
    @PutMapping("/{recordId}/cid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecordResponse> updateRecordCID(
            @PathVariable String recordId,
            @RequestParam String newCid,
            @RequestParam String newHash) {
        try {
            String result = recordService.updateRecordCID(recordId, newCid, newHash);

            RecordResponse response = new RecordResponse(
                    true,
                    "Record CID updated successfully",
                    null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            RecordResponse response = new RecordResponse(
                    false,
                    "Failed to update CID: " + e.getMessage(),
                    null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Delete a record (soft delete)
     * DELETE /api/records/{recordId}
     */
    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecordResponse> deleteRecord(@PathVariable String recordId) {
        try {
            String result = recordService.deleteRecord(recordId);

            RecordResponse response = new RecordResponse(
                    true,
                    "Record deleted successfully",
                    null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            RecordResponse response = new RecordResponse(
                    false,
                    "Failed to delete record: " + e.getMessage(),
                    null);
            return ResponseEntity.badRequest().body(response);
        }
    }
}
