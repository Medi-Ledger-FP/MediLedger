package com.mediledger.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * File Service
 * Combines encryption and IPFS storage for secure medical file management
 */
@Service
public class FileService {

    private final EncryptionService encryptionService;
    private final IPFSService ipfsService;
    private final RecordService recordService;

    public FileService(EncryptionService encryptionService,
            IPFSService ipfsService,
            RecordService recordService) {
        this.encryptionService = encryptionService;
        this.ipfsService = ipfsService;
        this.recordService = recordService;
    }

    /**
     * Upload file: Encrypt → IPFS → Store CID on blockchain
     *
     * @param file       Multipart file
     * @param patientId  Patient identifier
     * @param recordType Type (e.g., "Lab Report", "X-Ray")
     * @param department Department
     * @return Record ID
     */
    public FileUploadResult uploadFile(MultipartFile file, String patientId,
            String recordType, String department) throws Exception {
        // 1. Read file bytes
        byte[] originalBytes = file.getBytes();

        // 2. Encrypt file
        byte[] encryptedBytes = encryptionService.encrypt(originalBytes);

        // 3. Calculate hash of encrypted file (for integrity)
        String fileHash = encryptionService.calculateHash(encryptedBytes);

        // 4. Upload to IPFS
        String ipfsCid = ipfsService.uploadFile(encryptedBytes, file.getOriginalFilename());

        // 5. Store metadata on blockchain
        String recordId = recordService.createRecord(
                patientId,
                ipfsCid,
                fileHash,
                recordType,
                department);

        return new FileUploadResult(recordId, ipfsCid, fileHash);
    }

    /**
     * Download file: Get CID from blockchain → Download from IPFS → Decrypt
     *
     * @param recordId Record identifier
     * @return Decrypted file bytes
     */
    public FileDownloadResult downloadFile(String recordId) throws Exception {
        // 1. Get record metadata from blockchain
        String recordJson = recordService.getRecord(recordId);

        // TODO: Parse JSON properly (for now, simulate)
        // In production, use Jackson or Gson
        String ipfsCid = extractCIDFromRecord(recordJson);
        String fileHash = extractHashFromRecord(recordJson);

        // 2. Download encrypted file from IPFS
        byte[] encryptedBytes = ipfsService.downloadFile(ipfsCid);

        // 3. Verify integrity
        String downloadedHash = encryptionService.calculateHash(encryptedBytes);
        if (!downloadedHash.equals(fileHash)) {
            throw new SecurityException("File integrity check failed! Possible tampering detected.");
        }

        // 4. Decrypt file
        byte[] decryptedBytes = encryptionService.decrypt(encryptedBytes);

        return new FileDownloadResult(decryptedBytes, ipfsCid);
    }

    /**
     * Delete file: Remove from blockchain (IPFS files remain for immutability)
     */
    public void deleteFile(String recordId) throws Exception {
        recordService.deleteRecord(recordId);
        // Note: IPFS files are not deleted (immutable storage)
    }

    // Helper methods for JSON parsing (temporary - replace with proper JSON
    // library)
    private String extractCIDFromRecord(String json) {
        // Placeholder: parse {"ipfsCid":"..."}
        if (json.contains("placeholder")) {
            return "QmSIM_TestCID123";
        }
        int start = json.indexOf("\"ipfsCid\":\"") + 11;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String extractHashFromRecord(String json) {
        // Placeholder: parse {"fileHash":"..."}
        if (json.contains("placeholder")) {
            return "placeholder_hash";
        }
        int start = json.indexOf("\"fileHash\":\"") + 12;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // Result classes
    public static class FileUploadResult {
        public final String recordId;
        public final String ipfsCid;
        public final String fileHash;

        public FileUploadResult(String recordId, String ipfsCid, String fileHash) {
            this.recordId = recordId;
            this.ipfsCid = ipfsCid;
            this.fileHash = fileHash;
        }
    }

    public static class FileDownloadResult {
        public final byte[] fileBytes;
        public final String ipfsCid;

        public FileDownloadResult(byte[] fileBytes, String ipfsCid) {
            this.fileBytes = fileBytes;
            this.ipfsCid = ipfsCid;
        }
    }
}
