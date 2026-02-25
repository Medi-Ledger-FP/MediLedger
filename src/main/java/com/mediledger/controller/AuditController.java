package com.mediledger.controller;

import com.mediledger.dto.AuditDTO.AuditQueryRequest;
import com.mediledger.dto.AuditDTO.AuditResponse;
import com.mediledger.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST Controller for Audit Logging
 */
@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<?> queryAuditLog(@RequestBody AuditQueryRequest request) {
        try {
            String result = auditService.queryAuditLog(
                    request.getPatientId(),
                    request.getStartDate(),
                    request.getEndDate());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<?> getPatientAuditLog(
            @PathVariable String patientId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            if (startDate == null) {
                startDate = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS).toString();
            }
            if (endDate == null) {
                endDate = java.time.Instant.now().toString();
            }

            return ResponseEntity.ok(auditService.queryAuditLog(patientId, startDate, endDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/failed-access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> queryFailedAccess(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            return ResponseEntity.ok(auditService.queryAuditByAction("DENIED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/compliance/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<?> generateComplianceReport(@PathVariable String patientId) {
        try {
            return ResponseEntity.ok(auditService.generateComplianceReport(patientId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/stats/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<?> getAuditStatistics(@PathVariable String patientId) {
        try {
            // Use compliance report which includes statistics
            return ResponseEntity.ok(auditService.generateComplianceReport(patientId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/log")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuditResponse> logAccess(
            @RequestParam String action,
            @RequestParam String recordId,
            @RequestParam boolean success,
            HttpServletRequest request) {
        try {
            String userId = org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();

            auditService.logAccess(userId, "UNKNOWN", action,
                    recordId, "UNKNOWN",
                    success ? "SUCCESS" : "DENIED",
                    request.getRemoteAddr(), action);

            return ResponseEntity.ok(new AuditResponse(true, "Logged", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuditResponse(false, e.getMessage(), null));
        }
    }
}
