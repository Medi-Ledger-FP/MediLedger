package com.mediledger.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emergency Access Service
 *
 * Implements a threshold-based emergency override using Shamir's Secret
 * Sharing:
 * 1. A patient's file AES key is split into N shares (default 5) at upload
 * time.
 * 2. Each share is assigned to a trusted stakeholder (e.g. hospital admin, govt
 * authority).
 * 3. An emergency requester opens a request.
 * 4. Stakeholders approve by submitting their share.
 * 5. Once K shares (default 3) are collected, the AES key is reconstructed.
 * 6. Temporary access is granted and the entire event is audit-logged.
 */
@Service
public class EmergencyAccessService {

    private static final int DEFAULT_TOTAL_SHARES = 5;
    private static final int DEFAULT_THRESHOLD = 3;
    private static final String SHARES_DIR = "data/sss-shares";

    private final ShamirSecretSharingService sss;
    private final AuditService auditService;

    // In-memory request store (requests are session-scoped — not persisted)
    private final Map<String, EmergencyRequest> requests = new ConcurrentHashMap<>();

    // Once access is granted, store: requestId → GrantedAccess(recordId, keyHex)
    private final Map<String, GrantedAccess> grantedAccessMap = new ConcurrentHashMap<>();

    // Working cache: patientId → list of encoded shares (backed by disk)
    private final Map<String, List<String>> keyShareRegistry = new ConcurrentHashMap<>();

    public EmergencyAccessService(ShamirSecretSharingService sss, AuditService auditService) {
        this.sss = sss;
        this.auditService = auditService;
        // Load any previously persisted shares into working cache
        loadSharesFromDisk();
    }

    // ── Key Split (called at file upload time) ───────────────────────────────

    /**
     * Split a patient's AES key into threshold shares and store them.
     * Returns the N encoded shares (each would be sent to a different stakeholder
     * in production).
     */
    public List<String> splitAndStoreKey(String patientId, byte[] aesKey) {
        List<ShamirSecretSharingService.SecretShare> shares = sss.split(aesKey, DEFAULT_TOTAL_SHARES,
                DEFAULT_THRESHOLD);

        List<String> encodedShares = new ArrayList<>();
        for (ShamirSecretSharingService.SecretShare share : shares) {
            encodedShares.add(sss.encodeShare(share));
        }

        keyShareRegistry.put(patientId, encodedShares);
        saveSharesToDisk(patientId, encodedShares); // persist to disk
        System.out.println("🔐 SSS: Stored " + DEFAULT_TOTAL_SHARES + " shares for patient " + patientId
                + " (threshold=" + DEFAULT_THRESHOLD + ") — persisted to disk");
        return encodedShares;
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    private void saveSharesToDisk(String patientId, List<String> shares) {
        try {
            Path dir = Paths.get(SHARES_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitise(patientId) + ".json");
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < shares.size(); i++) {
                json.append("  \"").append(shares.get(i)).append('"');
                if (i < shares.size() - 1)
                    json.append(',');
                json.append('\n');
            }
            json.append(']');
            Files.writeString(file, json.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("⚠️  Cannot persist SSS shares for " + patientId + ": " + e.getMessage());
        }
    }

    private void loadSharesFromDisk() {
        try {
            Path dir = Paths.get(SHARES_DIR);
            if (Files.notExists(dir))
                return;
            Files.list(dir).filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    String raw = Files.readString(p);
                    List<String> shares = new ArrayList<>();
                    // Simple JSON array parse
                    for (String part : raw.replaceAll("[\\[\\]\\n ]", "").split(",")) {
                        String s = part.replace("\"", "").trim();
                        if (!s.isEmpty())
                            shares.add(s);
                    }
                    String patientId = p.getFileName().toString().replace(".json", "");
                    keyShareRegistry.put(patientId, shares);
                    System.out.println("📂 Loaded " + shares.size() + " SSS shares for patient " + patientId);
                } catch (IOException e) {
                    System.err.println("⚠️  Failed to load shares from " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("⚠️  Cannot load SSS shares from disk: " + e.getMessage());
        }
    }

    private String sanitise(String patientId) {
        return patientId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // ── Emergency Request Flow ───────────────────────────────────────────────

    /**
     * Step 1: Emergency requester opens a request.
     */
    public EmergencyRequest openRequest(String requesterId, String patientId,
            String recordId, String reason, String requestedBy) {
        String requestId = "EMRG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        EmergencyRequest req = new EmergencyRequest(
                requestId, patientId, recordId, requesterId, reason,
                requestedBy, Instant.now(), DEFAULT_THRESHOLD,
                new ArrayList<>(), EmergencyStatus.PENDING);
        requests.put(requestId, req);

        // Audit log
        auditService.logAccess("EMERGENCY_REQUEST", requesterId, patientId,
                "Emergency access requested for record " + recordId + ": " + reason);

        System.out.println("🚨 Emergency request " + requestId + " opened by " + requesterId
                + " for record " + recordId);
        return req;
    }

    /** Legacy overload without recordId */
    public EmergencyRequest openRequest(String requesterId, String patientId,
            String reason, String requestedBy) {
        return openRequest(requesterId, patientId, "UNKNOWN", reason, requestedBy);
    }

    /**
     * Step 2: A stakeholder approves by submitting their share.
     * shareIndex is 1-based (which of the N shares this stakeholder holds).
     */
    public ApprovalResult approveRequest(String requestId, String approverId, int shareIndex) {
        EmergencyRequest req = requests.get(requestId);
        if (req == null)
            throw new IllegalArgumentException("Request not found: " + requestId);
        if (req.status() == EmergencyStatus.APPROVED) {
            return new ApprovalResult(requestId, true, "Already approved", null);
        }
        if (req.status() == EmergencyStatus.DENIED) {
            return new ApprovalResult(requestId, false, "Request was denied", null);
        }

        // Retrieve this stakeholder's share from registry
        List<String> patientShares = keyShareRegistry.get(req.patientId());
        if (patientShares == null || shareIndex < 1 || shareIndex > patientShares.size()) {
            throw new IllegalArgumentException("Invalid share index: " + shareIndex);
        }

        String encodedShare = patientShares.get(shareIndex - 1);
        req.collectedShares().add(encodedShare);

        auditService.logAccess("EMERGENCY_APPROVAL", approverId, req.patientId(),
                "Share " + shareIndex + " submitted for request " + requestId);

        System.out.println("✅ Share " + shareIndex + "/" + DEFAULT_TOTAL_SHARES
                + " collected for request " + requestId + " (have "
                + req.collectedShares().size() + "/" + req.threshold() + " needed)");

        // Check if we've hit the threshold
        if (req.collectedShares().size() >= req.threshold()) {
            return reconstructAndGrant(req);
        }

        int remaining = req.threshold() - req.collectedShares().size();
        return new ApprovalResult(requestId, false,
                "Share accepted. Need " + remaining + " more approvals.", null);
    }

    /**
     * Step 3: Reconstruct the AES key and grant temporary access.
     */
    private ApprovalResult reconstructAndGrant(EmergencyRequest req) {
        try {
            // Decode shares
            List<ShamirSecretSharingService.SecretShare> shares = new ArrayList<>();
            for (String encoded : req.collectedShares()) {
                shares.add(sss.decodeShare(encoded));
            }

            // Reconstruct the AES key
            byte[] reconstructedKey = sss.reconstruct(shares);
            String keyHex = bytesToHex(reconstructedKey);

            // Update request status
            EmergencyRequest approved = new EmergencyRequest(
                    req.requestId(), req.patientId(), req.recordId(), req.requesterId(), req.reason(),
                    req.requestedBy(), req.timestamp(), req.threshold(),
                    req.collectedShares(), EmergencyStatus.APPROVED);
            requests.put(req.requestId(), approved);

            auditService.logAccess("EMERGENCY_GRANTED", req.requesterId(), req.patientId(),
                    "Emergency access GRANTED via threshold cryptography. RequestId=" + req.requestId());

            System.out.println("🔓 Emergency access GRANTED for request " + req.requestId()
                    + " — AES key reconstructed from " + req.collectedShares().size() + " shares");

            // Persist granted access so the download endpoint can use it
            grantedAccessMap.put(req.requestId(), new GrantedAccess(req.recordId(), keyHex));

            return new ApprovalResult(req.requestId(), true,
                    "EMERGENCY ACCESS GRANTED. Threshold met (" + req.threshold() + "/" + req.threshold() + ").",
                    keyHex);

        } catch (Exception e) {
            return new ApprovalResult(req.requestId(), false,
                    "Key reconstruction failed: " + e.getMessage(), null);
        }
    }

    public EmergencyRequest getRequest(String requestId) {
        return requests.get(requestId);
    }

    /**
     * Returns the granted access (recordId + reconstructed AES key hex) for a
     * completed request
     */
    public GrantedAccess getGrantedAccess(String requestId) {
        return grantedAccessMap.get(requestId);
    }

    public List<EmergencyRequest> listRequests(String patientId) {
        return requests.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .toList();
    }

    public List<EmergencyRequest> listAllRequests() {
        return new ArrayList<>(requests.values());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ── Data records ──────────────────────────────────────────────────────────

    public enum EmergencyStatus {
        PENDING, APPROVED, DENIED
    }

    public record EmergencyRequest(
            String requestId,
            String patientId,
            String recordId, // which medical record this concerns
            String requesterId,
            String reason,
            String requestedBy,
            Instant timestamp,
            int threshold,
            List<String> collectedShares,
            EmergencyStatus status) {
    }

    public record ApprovalResult(
            String requestId,
            boolean granted,
            String message,
            String reconstructedKey // hex AES key — present only when granted == true
    ) {
    }

    /**
     * Holds the granted emergency access: which record and the AES key to decrypt
     * it
     */
    public record GrantedAccess(String recordId, String aesKeyHex) {
    }
}
