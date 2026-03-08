package com.mediledger.controller;

import com.mediledger.dto.ConsentDTO;
import com.mediledger.dto.ConsentDTO.GrantAccessRequest;
import com.mediledger.dto.ConsentDTO.ConsentResponse;
import com.mediledger.service.ConsentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Consent/Access Management
 * Manages patient consent for doctor access to records
 */
@RestController
@RequestMapping("/api/consent")
@CrossOrigin(origins = "*")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping("/grant")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConsentResponse> grantAccess(@RequestBody GrantAccessRequest request) {
        try {
            String grantId = consentService.grantAccess(
                    request.getPatientId(),
                    request.getDoctorId(),
                    request.getRecordId(),
                    request.getExpiresAt(),
                    request.getPurpose());

            ConsentResponse response = new ConsentResponse(true, "Access granted: " + grantId, null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ConsentResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/revoke/{grantId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConsentResponse> revokeAccess(@PathVariable String grantId) {
        try {
            consentService.revokeAccess(grantId);
            return ResponseEntity.ok(new ConsentResponse(true, "Access revoked", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ConsentResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<?> checkAccess(
            @RequestParam String doctorId,
            @RequestParam String recordId,
            @RequestParam String patientId) {
        try {
            boolean hasAccess = consentService.checkAccess(doctorId, recordId, patientId);
            return ResponseEntity.ok(new CheckAccessResponse(hasAccess));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/patient/{patientId}/doctors")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<?> listAuthorizedDoctors(@PathVariable String patientId) {
        try {
            // Returns proper JSON array: [{grantId, doctorId, recordId, purpose}, ...]
            return ResponseEntity.ok(consentService.listAuthorizedGrants(patientId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/doctor/{doctorId}/patients")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<?> listDoctorAccess(@PathVariable String doctorId) {
        try {
            return ResponseEntity.ok(consentService.listDoctorAccess(doctorId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{grantId}/expiration")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConsentResponse> updateExpiration(
            @PathVariable String grantId,
            @RequestParam String newExpiresAt) {
        try {
            consentService.updateExpiration(grantId, newExpiresAt);
            return ResponseEntity.ok(new ConsentResponse(true, "Expiration updated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ConsentResponse(false, e.getMessage(), null));
        }
    }

    private record CheckAccessResponse(boolean hasAccess) {
    }
}
