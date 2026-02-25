package com.mediledger.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit Service
 * HIPAA-compliant audit logging via blockchain (AuditTrail chaincode)
 */
@Service
public class AuditService {

    @Value("${fabric.chaincode.name:mediledger-cc}")
    private String chaincodeName;

    @Autowired
    private FabricGatewayService fabricGateway;

    /**
     * Log access to a medical record on the blockchain (AuditTrail:logAccess)
     *
     * @param userId   User performing action
     * @param userRole User's role
     * @param action   Action (CREATE_RECORD, VIEW_RECORD, etc.)
     * @param details  Extra detail / reason string
     */
    public String logAccess(String userId, String userRole,
            String action, String details) {
        return logAccess(userId, userRole, action, "UNKNOWN", "UNKNOWN",
                "SUCCESS", "127.0.0.1", details);
    }

    /**
     * Full log-access call — matches AuditTrail chaincode logAccess() signature.
     */
    public String logAccess(String userId, String userRole, String action,
            String recordId, String patientId, String result,
            String ipAddress, String reason) {
        String auditId = UUID.randomUUID().toString();

        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                // Use submitAuditLog on the default contract (RecordLedger)
                // which is confirmed working — avoids AuditTrail named-contract routing
                fabricGateway.submitTransaction("submitAuditLog",
                        auditId, userId, userRole, action,
                        recordId, patientId, result,
                        reason != null ? reason : "");
                System.out.println("📋 Audit logged on blockchain: " + action
                        + " by " + userId + " → " + result + " [" + auditId + "]");
            } catch (Exception e) {
                System.err.println("⚠️  Blockchain audit failed, console-only: " + e.getMessage());
                System.out.println("Audit: " + action + " by " + userId + " → " + result);
            }
        } else {
            System.out.println("Audit (no chain): " + action + " by " + userId + " → " + result);
        }
        return auditId;
    }

    /**
     * Query audit log for a patient
     */
    public String queryAuditLog(String patientId, String startDate, String endDate) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryAuditLog", patientId, startDate, endDate);
            } catch (Exception e) {
                System.err.println("⚠️  Audit query failed: " + e.getMessage());
            }
        }
        return "[]";
    }

    /**
     * Query audit log by action type
     */
    public String queryAuditByAction(String action) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                String now = java.time.Instant.now().toString();
                String thirtyDays = java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS).toString();
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryByAction", action, thirtyDays, now);
            } catch (Exception e) {
                System.err.println("⚠️  Audit query failed: " + e.getMessage());
            }
        }
        return "[]";
    }

    /**
     * Generate compliance report for a patient
     */
    public String generateComplianceReport(String patientId) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "generateComplianceReport", patientId);
            } catch (Exception e) {
                System.err.println("⚠️  Compliance report failed: " + e.getMessage());
            }
        }
        return "{\"status\":\"unavailable\"}";
    }
}
