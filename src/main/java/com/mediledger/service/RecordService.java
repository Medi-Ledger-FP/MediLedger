package com.mediledger.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Record Service
 * Manages medical record operations.
 * Uses live Fabric blockchain when available, falls back to in-memory store.
 */
@Service
public class RecordService {

    @Autowired
    private FabricGatewayService fabricGateway;

    @Value("${fabric.chaincode.name:mediledger-cc}")
    private String chaincodeName;

    // In-memory fallback store
    private final Map<String, Map<String, String>> records = new ConcurrentHashMap<>();
    private final Map<String, List<String>> patientIndex = new ConcurrentHashMap<>();

    /**
     * Create a new medical record — writes to blockchain if available, otherwise
     * in-memory
     */
    public String createRecord(String patientId, String ipfsCid, String fileHash,
            String recordType, String department, String serialisedABE) {
        String recordId = UUID.randomUUID().toString();
        String abe = (serialisedABE != null) ? serialisedABE : "";

        // Store in-memory IMMEDIATELY — response returns to client at once
        storeInMemory(recordId, patientId, ipfsCid, fileHash, recordType, department, abe);
        System.out.println("📝 Record stored in-memory: " + recordId);

        // Async blockchain write — fire and forget, does NOT block HTTP response
        if (fabricGateway.isAvailable()) {
            final String finalAbe = abe;
            CompletableFuture.runAsync(() -> {
                try {
                    fabricGateway.submitContractTransaction("RecordLedger", "createRecord",
                            recordId, patientId, ipfsCid, fileHash, recordType, department, finalAbe);
                    System.out.println("✅ Record committed to blockchain: " + recordId);
                } catch (org.hyperledger.fabric.client.EndorseException e) {
                    // EndorseException carries the actual peer/chaincode error message
                    System.err.println("⚠️  Chaincode ENDORSE failed for createRecord:");
                    System.err.println("   Message: " + e.getMessage());
                    System.err.println("   gRPC Status: " + e.getStatus().getCode()
                            + " | " + e.getStatus().getDescription());
                    if (e.getCause() != null) {
                        System.err.println("   Cause: " + e.getCause().getMessage());
                    }
                    System.err.println("   → Falling back to in-memory (record still saved).");
                } catch (Exception e) {
                    System.err.println("⚠️  Async blockchain write failed (in-memory active): " + e.getMessage());
                    System.err.println("   Full cause: " + (e.getCause() != null ? e.getCause().getMessage() : "none"));
                }
            });
        }

        return recordId;
    }

    /** Convenience overload — no ABE policy (legacy) */
    public String createRecord(String patientId, String ipfsCid, String fileHash,
            String recordType, String department) {
        return createRecord(patientId, ipfsCid, fileHash, recordType, department, "");
    }

    /**
     * Get a single record by ID
     */
    public String getRecord(String recordId) {
        if (fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction("RecordLedger", "getRecord", recordId);
            } catch (Exception e) {
                System.err.println("⚠️  Chaincode query failed: " + e.getMessage());
            }
        }
        Map<String, String> record = records.get(recordId);
        if (record == null)
            return "{\"recordId\":\"" + recordId + "\",\"status\":\"NOT_FOUND\"}";
        return mapToJson(record);
    }

    /**
     * Get the patientId for a given recordId from the in-memory store.
     * Used by the consent gate in FileController.
     */
    public String getPatientIdForRecord(String recordId) {
        Map<String, String> record = records.get(recordId);
        return record != null ? record.get("patientId") : null;
    }

    /**
     * Query all records for a patient — blockchain or in-memory
     */
    public String getRecordsByPatient(String patientId) {
        if (fabricGateway.isAvailable()) {
            try {
                return fabricGateway.evaluateContractTransaction("RecordLedger", "queryRecordsByPatient", patientId);
            } catch (Exception e) {
                System.err.println("⚠️  Chaincode query failed: " + e.getMessage());
            }
        }
        return buildJsonArray(patientIndex.getOrDefault(patientId, Collections.emptyList()));
    }

    /**
     * List records as Java objects for REST API serialization
     */
    public List<Map<String, String>> getRecordsByPatientAsList(String patientId) {
        // Parse from JSON string (works for both blockchain and in-memory result)
        String json = getRecordsByPatient(patientId);
        return parseJsonArray(json, patientId);
    }

    /**
     * Update IPFS CID after re-encryption
     */
    public String updateRecordCID(String recordId, String newIpfsCid, String newFileHash) {
        if (fabricGateway.isAvailable()) {
            try {
                return fabricGateway.submitContractTransaction("RecordLedger", "updateRecordCID", recordId, newIpfsCid,
                        newFileHash);
            } catch (Exception e) {
                System.err.println("⚠️  Chaincode submit failed: " + e.getMessage());
            }
        }
        Map<String, String> record = records.get(recordId);
        if (record != null) {
            record.put("ipfsCid", newIpfsCid);
            record.put("fileHash", newFileHash);
        }
        return "{\"recordId\":\"" + recordId + "\",\"updated\":true}";
    }

    /**
     * Soft-delete a record
     */
    public String deleteRecord(String recordId) {
        if (fabricGateway.isAvailable()) {
            try {
                return fabricGateway.submitContractTransaction("RecordLedger", "deleteRecord", recordId);
            } catch (Exception e) {
                System.err.println("⚠️  Chaincode submit failed: " + e.getMessage());
            }
        }
        Map<String, String> record = records.get(recordId);
        if (record != null) {
            record.put("status", "DELETED");
        }
        return "{\"message\":\"Record marked as deleted\"}";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void storeInMemory(String recordId, String patientId, String ipfsCid,
            String fileHash, String recordType, String department, String abePolicy) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("recordId", recordId);
        record.put("patientId", patientId);
        record.put("ipfsCid", ipfsCid);
        record.put("fileHash", fileHash);
        record.put("recordType", recordType);
        record.put("department", department);
        record.put("abePolicy", abePolicy != null ? abePolicy : "");
        record.put("timestamp", Instant.now().toString());
        record.put("status", "ACTIVE");
        records.put(recordId, record);
        patientIndex.computeIfAbsent(patientId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(recordId);
    }

    /** Legacy overload without ABE */
    private void storeInMemory(String recordId, String patientId, String ipfsCid,
            String fileHash, String recordType, String department) {
        storeInMemory(recordId, patientId, ipfsCid, fileHash, recordType, department, "");
    }

    private String buildJsonArray(List<String> ids) {
        if (ids.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (String id : ids) {
            Map<String, String> r = records.get(id);
            if (r != null) {
                if (sb.length() > 1)
                    sb.append(",");
                sb.append(mapToJson(r));
            }
        }
        return sb.append("]").toString();
    }

    /**
     * Best-effort JSON array parser — returns in-memory records on parse failure
     */
    private List<Map<String, String>> parseJsonArray(String json, String patientId) {
        List<Map<String, String>> result = new ArrayList<>();
        // If the result is from in-memory (not blockchain), return directly
        List<String> ids = patientIndex.getOrDefault(patientId, Collections.emptyList());
        if (!ids.isEmpty()) {
            for (String id : ids) {
                Map<String, String> r = records.get(id);
                if (r != null)
                    result.add(r);
            }
            if (!result.isEmpty())
                return result;
        }
        // Try to parse the JSON array from blockchain response
        try {
            if (json == null || json.equals("[]") || json.equals("null"))
                return result;
            // Simple parse for flat JSON objects in array
            String[] parts = json.substring(1, json.length() - 1).split("\\},\\{");
            for (String part : parts) {
                part = part.replaceAll("^\\{", "").replaceAll("\\}$", "");
                Map<String, String> map = new LinkedHashMap<>();
                for (String kv : part.split(",\"")) {
                    kv = kv.replaceAll("^\"|\"$", "");
                    int colon = kv.indexOf("\":\"");
                    if (colon > 0) {
                        map.put(kv.substring(0, colon).trim().replaceAll("\"", ""),
                                kv.substring(colon + 3).replaceAll("\"$", ""));
                    }
                }
                if (!map.isEmpty())
                    result.add(map);
            }
        } catch (Exception e) {
            /* return empty */ }
        return result;
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> {
            if (sb.length() > 1)
                sb.append(",");
            sb.append("\"").append(k).append("\":\"").append(v.replace("\"", "\\\"")).append("\"");
        });
        return sb.append("}").toString();
    }
}
