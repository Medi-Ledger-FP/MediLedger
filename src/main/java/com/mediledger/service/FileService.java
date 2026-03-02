package com.mediledger.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * File Service — Full Encryption Pipeline
 *
 * Upload workflow:
 * 1. Read file bytes
 * 2. Encrypt file with AES-256-GCM → encryptedFile
 * 3. Extract the AES key used
 * 4. Encrypt AES key under CP-ABE policy (RSA per-role)
 * 5. Split AES key into SSS shares (for emergency access)
 * 6. Upload encryptedFile → IPFS → get CID
 * 7. Store {CID, fileHash, ABE ciphertext, policy} on blockchain
 *
 * Download workflow (authorised user):
 * 1. Fetch record metadata from blockchain
 * 2. Check role matches CP-ABE policy
 * 3. Decrypt AES key with user's role key
 * 4. Download encrypted file from IPFS
 * 5. Verify SHA-256 integrity hash
 * 6. Decrypt file with AES key
 */
@Service
public class FileService {

    private final EncryptionService encryptionService;
    private final IPFSService ipfsService;
    private final RecordService recordService;
    private final ABEService abeService;
    private final EmergencyAccessService emergencyService;
    private final AuditService auditService;

    public FileService(EncryptionService encryptionService,
            IPFSService ipfsService,
            RecordService recordService,
            ABEService abeService,
            EmergencyAccessService emergencyService,
            AuditService auditService) {
        this.encryptionService = encryptionService;
        this.ipfsService = ipfsService;
        this.recordService = recordService;
        this.abeService = abeService;
        this.emergencyService = emergencyService;
        this.auditService = auditService;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Full upload pipeline with ABE key encryption + SSS key splitting.
     *
     * @param file         The medical file (PDF, DICOM, etc.)
     * @param patientId    Owner patient ID
     * @param recordType   "Lab Report", "X-Ray", etc.
     * @param department   "Cardiology", "General", etc.
     * @param uploaderRole Role of the uploader (for ABE policy)
     */
    public FileUploadResult uploadFile(MultipartFile file, String patientId,
            String recordType, String department,
            String uploaderRole) throws Exception {
        // ── Step 1: Read file ──────────────────────────────────────────────
        byte[] originalBytes = file.getBytes();

        // ── Append file extension to recordType so download can restore it ─
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = "|" + originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedRecordType = recordType + extension;
        System.out.println("📎 Storing recordType with extension: " + storedRecordType);

        // ── Step 2: AES-256-GCM encrypt ───────────────────────────────────
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes);

        // ── Step 3: Get the raw AES key (for ABE + SSS) ───────────────────
        byte[] aesKey = encryptionService.getLastKey();

        // ── Step 4: CP-ABE policy key encryption ──────────────────────────
        Set<String> policy = abeService.buildPolicy(storedRecordType, department,
                uploaderRole != null ? uploaderRole : "PATIENT");
        ABEService.ABECiphertext abeCiphertext = abeService.encryptKey(aesKey, policy);
        String serialisedABE = abeService.serialise(abeCiphertext);

        // ── Step 5: Shamir split for emergency access ──────────────────────
        List<String> sssShares = emergencyService.splitAndStoreKey(patientId, aesKey);

        // ── Step 6: Upload encrypted file to IPFS ─────────────────────────
        String fileHash = encryptionService.calculateHash(encryptedBytes);
        String ipfsCid = ipfsService.uploadFile(encryptedBytes, originalFilename);

        // ── Step 7: Store metadata on blockchain (includes ABE policy) ────
        System.out.println("📋 ABE Policy (persisting to chain): "
                + serialisedABE.substring(0, Math.min(60, serialisedABE.length())) + "...");
        String recordId = recordService.createRecord(
                patientId, ipfsCid, fileHash, storedRecordType, department, serialisedABE);

        System.out.printf(
                "✅ Upload complete: recordId=%s cid=%s policy=%s shares=%d%n",
                recordId, ipfsCid, policy, sssShares.size());

        // ── Audit log: record creation ─────────────────────────────────────
        auditService.logAccess(patientId, uploaderRole != null ? uploaderRole : "PATIENT",
                "CREATE_RECORD", recordId, patientId, "SUCCESS", "backend",
                "File upload: " + storedRecordType + " / " + department);

        return new FileUploadResult(recordId, ipfsCid, fileHash,
                String.join(",", policy), sssShares.size());
    }

    /**
     * Upload with patient-defined access policy.
     * 
     * @param customAllowedRoles comma-separated roles (e.g. "DOCTOR,CARDIOLOGY"),
     *                           or null for default
     */
    public FileUploadResult uploadFile(MultipartFile file, String patientId,
            String recordType, String department,
            String uploaderRole, String customAllowedRoles) throws Exception {
        // Append original file extension to recordType to preserve it
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = "|" + originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedRecordType = recordType + extension;

        byte[] originalBytes = file.getBytes();
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes);
        byte[] aesKey = encryptionService.getLastKey();

        // Patient-controlled policy: override default with custom roles
        Set<String> policy = abeService.buildPolicy(storedRecordType, department,
                uploaderRole != null ? uploaderRole : "PATIENT", customAllowedRoles);
        ABEService.ABECiphertext abeCiphertext = abeService.encryptKey(aesKey, policy);
        String serialisedABE = abeService.serialise(abeCiphertext);

        List<String> sssShares = emergencyService.splitAndStoreKey(patientId, aesKey);
        String fileHash = encryptionService.calculateHash(encryptedBytes);
        String ipfsCid = ipfsService.uploadFile(encryptedBytes, originalFilename);

        System.out.println("📋 ABE Policy (patient-controlled: " + String.join(",", policy)
                + ") | CID: " + ipfsCid);
        String recordId = recordService.createRecord(
                patientId, ipfsCid, fileHash, storedRecordType, department, serialisedABE);

        auditService.logAccess(patientId, uploaderRole != null ? uploaderRole : "PATIENT",
                "CREATE_RECORD", recordId, patientId, "SUCCESS", "backend",
                "Upload with custom policy: " + String.join(",", policy));

        return new FileUploadResult(recordId, ipfsCid, fileHash,
                String.join(",", policy), sssShares.size());
    }

    /** Convenience overload — default uploader role = PATIENT */
    public FileUploadResult uploadFile(MultipartFile file, String patientId,
            String recordType, String department) throws Exception {
        return uploadFile(file, patientId, recordType, department, "PATIENT");
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Download + decrypt a file, enforcing CP-ABE role check.
     *
     * @param recordId Record to fetch
     * @param userRole Requesting user's role (checked against policy)
     */
    public FileDownloadResult downloadFile(String recordId, String userRole) throws Exception {
        // 1. Get record metadata
        String recordJson = recordService.getRecord(recordId);
        String ipfsCid = extractField(recordJson, "ipfsCid", "QmSIM_TestCID123");
        String fileHash = extractField(recordJson, "fileHash", "placeholder_hash");
        String abeRaw = extractField(recordJson, "abePolicy", null);

        byte[] aesKey;

        if (abeRaw != null && !abeRaw.isEmpty()) {
            // 2a. Real path: decrypt AES key via CP-ABE role check
            ABEService.ABECiphertext ct = abeService.deserialise(abeRaw);
            aesKey = abeService.decryptKey(ct, userRole != null ? userRole : "PATIENT");
        } else {
            // 2b. Fallback: use stored key from EncryptionService (simulation)
            aesKey = encryptionService.getLastKey();
        }

        // 3. Download encrypted file from IPFS
        byte[] encryptedBytes = ipfsService.downloadFile(ipfsCid);

        // 4. Integrity check
        String downloadedHash = encryptionService.calculateHash(encryptedBytes);
        if (!downloadedHash.equals(fileHash) && !fileHash.equals("placeholder_hash")) {
            throw new SecurityException("File integrity check failed! Possible tampering detected.");
        }

        // 5. Decrypt with the recovered AES key
        byte[] decryptedBytes = encryptionService.decryptWithKey(encryptedBytes, aesKey);
        String storedRecordType = extractField(recordJson, "recordType", "Medical Record");

        return new FileDownloadResult(decryptedBytes, ipfsCid, storedRecordType);
    }

    /** Convenience: download without role check (PATIENT downloading own file) */
    public FileDownloadResult downloadFile(String recordId) throws Exception {
        return downloadFile(recordId, "PATIENT");
    }

    /**
     * Emergency download: use the SSS-reconstructed AES key to decrypt a file.
     * Called after threshold approvals grant emergency access.
     *
     * @param recordId  Medical record ID
     * @param aesKeyHex Hex-encoded AES key from SSS reconstruction
     */
    public FileDownloadResult downloadWithEmergencyKey(String recordId, String aesKeyHex) throws Exception {
        // 1. Get record metadata from blockchain / in-memory fallback
        String recordJson = recordService.getRecord(recordId);
        String ipfsCid = extractField(recordJson, "ipfsCid", null);
        if (ipfsCid == null || ipfsCid.isBlank()) {
            throw new IllegalStateException("Record not found or no IPFS CID: " + recordId);
        }

        // 2. Convert hex key back to bytes
        byte[] aesKey = hexToBytes(aesKeyHex);

        // 3. Download encrypted file from IPFS
        byte[] encryptedBytes = ipfsService.downloadFile(ipfsCid);

        // 4. Decrypt with the reconstructed AES key
        byte[] decryptedBytes = encryptionService.decryptWithKey(encryptedBytes, aesKey);
        String storedRecordType = extractField(recordJson, "recordType", "Medical Record");

        System.out.println("🔓 Emergency download complete for record " + recordId);
        return new FileDownloadResult(decryptedBytes, ipfsCid, storedRecordType);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /** Delete file metadata from blockchain (IPFS is immutable). */
    public void deleteFile(String recordId) throws Exception {
        recordService.deleteRecord(recordId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractField(String json, String field, String defaultVal) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0)
            return defaultVal;
        start += key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : defaultVal;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public static class FileUploadResult {
        public final String recordId;
        public final String ipfsCid;
        public final String fileHash;
        public final String abePolicy;
        public final int sssShares;

        public FileUploadResult(String recordId, String ipfsCid,
                String fileHash, String abePolicy, int sssShares) {
            this.recordId = recordId;
            this.ipfsCid = ipfsCid;
            this.fileHash = fileHash;
            this.abePolicy = abePolicy;
            this.sssShares = sssShares;
        }
    }

    public static class FileDownloadResult {
        public final byte[] fileBytes;
        public final String ipfsCid;
        public final String recordType;

        public FileDownloadResult(byte[] fileBytes, String ipfsCid, String recordType) {
            this.fileBytes = fileBytes;
            this.ipfsCid = ipfsCid;
            this.recordType = recordType;
        }
    }
}
