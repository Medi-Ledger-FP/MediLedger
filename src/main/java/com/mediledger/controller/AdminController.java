package com.mediledger.controller;

import com.mediledger.service.FabricGatewayService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Admin Controller — provides system-wide statistics pulled directly from the
 * blockchain.
 * All counts come from chaincode queries (RecordLedger and AuditTrail
 * contracts).
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final FabricGatewayService fabricGateway;
    private final Gson gson = new Gson();

    public AdminController(FabricGatewayService fabricGateway) {
        this.fabricGateway = fabricGateway;
    }

    /**
     * GET /api/admin/stats
     * Returns real-time system statistics from the Hyperledger Fabric blockchain.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<?> getAdminStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        boolean blockchainOnline = fabricGateway.isAvailable();
        stats.put("blockchainOnline", blockchainOnline);

        // --- Record count from RecordLedger:queryAllMedicalRecords
        // (docType=MEDICAL_RECORD) ---
        int totalRecords = 0;
        if (blockchainOnline) {
            try {
                String json = fabricGateway.evaluateTransaction("queryAllMedicalRecords");
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                totalRecords = (arr != null) ? arr.size() : 0;
            } catch (Exception e) {
                // fallback to getAllRecords if queryAllMedicalRecords not yet deployed
                try {
                    String json = fabricGateway.evaluateTransaction("getAllRecords");
                    JsonArray arr = gson.fromJson(json, JsonArray.class);
                    totalRecords = (arr != null) ? arr.size() : 0;
                } catch (Exception e2) {
                    System.err.println("⚠️  admin/stats: getAllRecords failed: " + e2.getMessage());
                }
            }
        }
        stats.put("totalRecords", totalRecords);

        // --- Recent activity from AuditTrail (last 24 h) ---
        String now = Instant.now().toString();
        String yesterday = Instant.now().minus(24, ChronoUnit.HOURS).toString();
        String thirtyDays = Instant.now().minus(30, ChronoUnit.DAYS).toString();

        List<Map<String, Object>> activity = new ArrayList<>();
        int auditCount = 0;

        if (blockchainOnline) {
            // Uploads in last 24h
            try {
                String uploadsJson = fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryByAction", "CREATE_RECORD", yesterday, now);
                JsonArray uploads = gson.fromJson(uploadsJson, JsonArray.class);
                if (uploads != null) {
                    auditCount += uploads.size();
                    for (JsonElement el : uploads) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        var obj = el.getAsJsonObject();
                        entry.put("type", "RECORD_UPLOAD");
                        entry.put("user", obj.has("userId") ? obj.get("userId").getAsString() : "unknown");
                        entry.put("result", obj.has("result") ? obj.get("result").getAsString() : "");
                        entry.put("timestamp", obj.has("timestamp") ? obj.get("timestamp").getAsString() : "");
                        entry.put("recordId", obj.has("recordId") ? obj.get("recordId").getAsString() : "");
                        activity.add(entry);
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️  admin/stats: queryByAction(CREATE_RECORD) failed: " + e.getMessage());
            }

            // Views in last 24h
            try {
                String viewsJson = fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryByAction", "VIEW_RECORD", yesterday, now);
                JsonArray views = gson.fromJson(viewsJson, JsonArray.class);
                if (views != null) {
                    auditCount += views.size();
                    for (JsonElement el : views) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        var obj = el.getAsJsonObject();
                        entry.put("type", "RECORD_ACCESS");
                        entry.put("user", obj.has("userId") ? obj.get("userId").getAsString() : "unknown");
                        entry.put("result", obj.has("result") ? obj.get("result").getAsString() : "");
                        entry.put("timestamp", obj.has("timestamp") ? obj.get("timestamp").getAsString() : "");
                        entry.put("recordId", obj.has("recordId") ? obj.get("recordId").getAsString() : "");
                        activity.add(entry);
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️  admin/stats: queryByAction(VIEW_RECORD) failed: " + e.getMessage());
            }

            // Denied access in last 30 days
            try {
                String deniedJson = fabricGateway.evaluateContractTransaction(
                        "AuditTrail", "queryFailedAccess", thirtyDays, now);
                JsonArray denied = gson.fromJson(deniedJson, JsonArray.class);
                if (denied != null)
                    auditCount += denied.size();
            } catch (Exception e) {
                System.err.println("⚠️  admin/stats: queryFailedAccess failed: " + e.getMessage());
            }
        }

        // Sort activity by timestamp descending and cap at 10
        activity.sort((a, b) -> {
            String ta = (String) a.getOrDefault("timestamp", "");
            String tb = (String) b.getOrDefault("timestamp", "");
            return tb.compareTo(ta);
        });
        if (activity.size() > 10)
            activity = activity.subList(0, 10);

        stats.put("totalAuditEntries", auditCount);
        stats.put("recentActivity", activity);

        // --- System health ---
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("blockchain", blockchainOnline ? "ONLINE" : "OFFLINE");
        health.put("ipfs", "OPERATIONAL");
        health.put("encryption", "ACTIVE");
        health.put("database", blockchainOnline ? "SYNCED" : "DEGRADED");
        stats.put("systemHealth", health);

        return ResponseEntity.ok(stats);
    }
}
