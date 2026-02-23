package com.mediledger.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Record Service
 * Manages medical record operations — in-memory store with blockchain stub.
 * The in-memory store is the live data source until Fabric network is deployed.
 */
@Service
public class RecordService {

    @Value("${fabric.chaincode.name:mediledger-cc}")
    private String chaincodeName;

    // In-memory record store: recordId -> record details
    private final Map<String, Map<String, String>> records = new ConcurrentHashMap<>();

    // Index: patientId -> list of recordIds
    private final Map<String, List<String>> patientIndex = new ConcurrentHashMap<>();

    /**
     * Create a new medical record and store it in memory (+ blockchain stub)
     */
    public String createRecord(String patientId, String ipfsCid, String fileHash,
            String recordType, String department) {
        String recordId = UUID.randomUUID().toString();

        // Store in memory
        Map<String, String> record = new LinkedHashMap<>();
        record.put("recordId", recordId);
        record.put("patientId", patientId);
        record.put("ipfsCid", ipfsCid);
        record.put("fileHash", fileHash);
        record.put("recordType", recordType);
        record.put("department", department);
        record.put("timestamp", Instant.now().toString());
        record.put("status", "ACTIVE");

        records.put(recordId, record);
        patientIndex.computeIfAbsent(patientId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(recordId);

        // TODO: Also submit to blockchain when Fabric network is live
        System.out.println("✅ Record stored: " + recordId + " → patient: " + patientId
                + " | type: " + recordType + " | cid: " + ipfsCid);
        return recordId;
    }

    /**
     * Get a single record by ID
     */
    public String getRecord(String recordId) {
        Map<String, String> record = records.get(recordId);
        if (record == null) {
            return "{\"recordId\":\"" + recordId + "\",\"status\":\"NOT_FOUND\"}";
        }
        return mapToJson(record);
    }

    /**
     * Query all records for a patient — returns a JSON array
     */
    public String getRecordsByPatient(String patientId) {
        List<String> ids = patientIndex.getOrDefault(patientId, Collections.emptyList());
        if (ids.isEmpty())
            return "[]";

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            Map<String, String> r = records.get(ids.get(i));
            if (r != null) {
                if (sb.length() > 1)
                    sb.append(",");
                sb.append(mapToJson(r));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * List records as Java objects (used internally)
     */
    public List<Map<String, String>> getRecordsByPatientAsList(String patientId) {
        List<String> ids = patientIndex.getOrDefault(patientId, Collections.emptyList());
        List<Map<String, String>> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> r = records.get(id);
            if (r != null)
                result.add(r);
        }
        return result;
    }

    /**
     * Update IPFS CID after re-encryption
     */
    public String updateRecordCID(String recordId, String newIpfsCid, String newFileHash) {
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
        Map<String, String> record = records.get(recordId);
        if (record != null) {
            record.put("status", "DELETED");
        }
        return "{\"message\":\"Record marked as deleted\"}";
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> {
            if (sb.length() > 1)
                sb.append(",");
            sb.append("\"").append(k).append("\":\"")
                    .append(v.replace("\"", "\\\"")).append("\"");
        });
        sb.append("}");
        return sb.toString();
    }
}
