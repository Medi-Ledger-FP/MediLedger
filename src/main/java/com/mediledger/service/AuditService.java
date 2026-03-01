package com.mediledger.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Audit Service
 * HIPAA-compliant audit logging via blockchain (AuditTrail chaincode).
 * Falls back to an in-memory log store so the audit UI works even without
 * a live blockchain connection.
 */
@Service
public class AuditService {

    @Value("${fabric.chaincode.name:mediledger-cc}")
    private String chaincodeName;

    @Autowired
    private FabricGatewayService fabricGateway;

    // In-memory audit log — thread-safe, holds all events since server start
    private final List<Map<String, String>> inMemoryLogs = new CopyOnWriteArrayList<>();

    /**
     * Log access to a medical record on the blockchain (AuditTrail:logAccess)
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

        // Always store in-memory so the audit UI works without blockchain
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("auditId", auditId);
        entry.put("userId", userId);
        entry.put("userRole", userRole);
        entry.put("action", action);
        entry.put("recordId", recordId != null ? recordId : "UNKNOWN");
        entry.put("patientId", patientId != null ? patientId : "UNKNOWN");
        entry.put("result", result != null ? result : "SUCCESS");
        entry.put("ipAddress", ipAddress != null ? ipAddress : "UNKNOWN");
        entry.put("reason", reason != null ? reason : "");
        entry.put("timestamp", Instant.now().toString());
        inMemoryLogs.add(entry);

        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                fabricGateway.submitContractTransaction("AuditTrail", "logAccess",
                        auditId, userId, userRole, action,
                        recordId != null ? recordId : "",
                        patientId != null ? patientId : "",
                        result != null ? result : "",
                        ipAddress != null ? ipAddress : "UNKNOWN",
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
     * Query audit log for a patient — blockchain first, in-memory fallback.
     */
    public String queryAuditLog(String patientId, String startDate, String endDate) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryAuditLog", patientId, startDate, endDate);
            } catch (Exception e) {
                System.err.println("⚠️  Audit query failed, using in-memory: " + e.getMessage());
            }
        }
        // In-memory fallback: filter by patientId
        List<Map<String, String>> filtered = inMemoryLogs.stream()
                .filter(e -> patientId == null || patientId.equals(e.get("patientId")))
                .collect(Collectors.toList());
        return toJsonArray(filtered);
    }

    /**
     * Query audit log by action type — blockchain first, in-memory fallback.
     */
    public String queryAuditByAction(String action) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                String now = Instant.now().toString();
                String thirtyDays = Instant.now().minus(30, ChronoUnit.DAYS).toString();
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryByAction", action, thirtyDays, now);
            } catch (Exception e) {
                System.err.println("⚠️  Audit query failed, using in-memory: " + e.getMessage());
            }
        }
        List<Map<String, String>> filtered = inMemoryLogs.stream()
                .filter(e -> action == null || action.equals(e.get("action")))
                .collect(Collectors.toList());
        return toJsonArray(filtered);
    }

    /**
     * Generate compliance report for a patient.
     */
    public String generateComplianceReport(String patientId) {
        if (fabricGateway != null && fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "generateComplianceReport", patientId);
            } catch (Exception e) {
                System.err.println("⚠️  Compliance report failed, using in-memory: " + e.getMessage());
            }
        }
        // In-memory compliance summary
        long total = inMemoryLogs.stream()
                .filter(e -> patientId == null || patientId.equals(e.get("patientId")))
                .count();
        long denied = inMemoryLogs.stream()
                .filter(e -> (patientId == null || patientId.equals(e.get("patientId")))
                        && "DENIED".equals(e.get("result")))
                .count();
        return String.format(
                "{\"patientId\":\"%s\",\"totalAccesses\":%d,\"deniedAccesses\":%d,\"source\":\"in-memory\"}",
                patientId, total, denied);
    }

    /**
     * Return all in-memory logs (for admin dashboard).
     */
    public List<Map<String, String>> getRecentLogs(int maxResults) {
        int size = inMemoryLogs.size();
        int from = Math.max(0, size - maxResults);
        // return most recent last→first
        List<Map<String, String>> recent = new ArrayList<>(inMemoryLogs.subList(from, size));
        Collections.reverse(recent);
        return recent;
    }

    /**
     * Return in-memory logs for a specific patient (for patient audit view).
     */
    public List<Map<String, String>> getLogsByPatient(String patientId) {
        return inMemoryLogs.stream()
                .filter(e -> patientId.equals(e.get("patientId")))
                .sorted(Comparator.comparing((Map<String, String> m) -> m.get("timestamp")).reversed())
                .collect(Collectors.toList());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String toJsonArray(List<Map<String, String>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append("{");
            list.get(i).forEach((k, v) -> {
                sb.append("\"").append(k).append("\":\"")
                        .append(v.replace("\"", "\\\"")).append("\",");
            });
            if (!list.get(i).isEmpty())
                sb.setLength(sb.length() - 1);
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
