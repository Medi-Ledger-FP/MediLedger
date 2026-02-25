package com.mediledger.chaincode;

import com.google.gson.Gson;
import com.mediledger.chaincode.model.MedicalRecord;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RecordLedger Contract
 * Manages medical record metadata on the blockchain
 * Actual encrypted files stored on IPFS
 */
@Contract(name = "RecordLedger", info = @Info(title = "Medical Record Ledger", description = "Manages medical record metadata with IPFS references", version = "1.0.0", license = @License(name = "Apache 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.html"), contact = @Contact(email = "contact@mediledger.com", name = "MediLedger Team")))
@Default
public class RecordLedger implements ContractInterface {

    private final Gson gson = new Gson();

    /**
     * Initialize the ledger
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void initLedger(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        System.out.println("RecordLedger initialized");
    }

    /**
     * Create a new medical record on the blockchain
     *
     * @param ctx        Context
     * @param recordId   Unique record identifier
     * @param patientId  Patient identifier
     * @param ipfsCid    IPFS Content Identifier
     * @param fileHash   SHA-256 hash of encrypted file
     * @param recordType Type of record (e.g., "Lab Report")
     * @param department Department (e.g., "Cardiology")
     * @return Created MedicalRecord as JSON
     */
    /**
     * Create a new medical record on the blockchain
     *
     * @param ctx        Context
     * @param recordId   Unique record identifier
     * @param patientId  Patient identifier
     * @param ipfsCid    IPFS Content Identifier
     * @param fileHash   SHA-256 hash of encrypted file
     * @param recordType Type of record (e.g., "Lab Report")
     * @param department Department (e.g., "Cardiology")
     * @param abePolicy  Serialised CP-ABE ciphertext (AES key encrypted per role)
     * @return Created MedicalRecord as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String createRecord(final Context ctx, String recordId, String patientId,
            String ipfsCid, String fileHash, String recordType,
            String department, String abePolicy) {
        ChaincodeStub stub = ctx.getStub();

        // Get caller identity
        String uploadedBy = ctx.getClientIdentity().getId();

        // Check if record already exists
        byte[] existingRecord = stub.getState(recordId);
        if (existingRecord != null && existingRecord.length > 0) {
            throw new RuntimeException("Record " + recordId + " already exists");
        }

        // Create timestamp
        String timestamp = Instant.now().toString();

        // Derive allowed roles from abePolicy (roles are the prefix before '|')
        String allowedRoles = "";
        if (abePolicy != null && abePolicy.contains("|")) {
            allowedRoles = abePolicy.split("\\|")[0]; // e.g. "ADMIN,PATIENT,DOCTOR"
        }

        // Create medical record with ABE policy
        MedicalRecord record = new MedicalRecord(
                recordId, patientId, ipfsCid, fileHash,
                uploadedBy, timestamp, recordType, department,
                abePolicy, allowedRoles);

        // Save to ledger
        stub.putState(recordId, record.toJSONString().getBytes());

        // Emit event
        stub.setEvent("RecordCreated", record.toJSONString().getBytes());

        System.out.println("Record created: " + recordId + " with abePolicy length=" +
                (abePolicy != null ? abePolicy.length() : 0));
        return record.toJSONString();
    }

    /**
     * Get a medical record by ID
     *
     * @param ctx      Context
     * @param recordId Record identifier
     * @return MedicalRecord as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getRecord(final Context ctx, String recordId) {
        ChaincodeStub stub = ctx.getStub();

        byte[] recordBytes = stub.getState(recordId);
        if (recordBytes == null || recordBytes.length == 0) {
            throw new RuntimeException("Record " + recordId + " does not exist");
        }

        return new String(recordBytes);
    }

    /**
     * Query all records for a specific patient
     *
     * @param ctx       Context
     * @param patientId Patient identifier
     * @return List of MedicalRecords as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryRecordsByPatient(final Context ctx, String patientId) {
        ChaincodeStub stub = ctx.getStub();

        // Build CouchDB query
        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\",\"status\":\"ACTIVE\"}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                patientId);

        List<MedicalRecord> records = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            MedicalRecord record = MedicalRecord.fromJSONString(result.getStringValue());
            records.add(record);
        }

        return gson.toJson(records);
    }

    /**
     * Update record metadata (e.g., new IPFS CID after re-encryption)
     *
     * @param ctx         Context
     * @param recordId    Record identifier
     * @param newIpfsCid  New IPFS CID
     * @param newFileHash New file hash
     * @return Updated MedicalRecord as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String updateRecordCID(final Context ctx, String recordId,
            String newIpfsCid, String newFileHash) {
        ChaincodeStub stub = ctx.getStub();

        // Get existing record
        byte[] recordBytes = stub.getState(recordId);
        if (recordBytes == null || recordBytes.length == 0) {
            throw new RuntimeException("Record " + recordId + " does not exist");
        }

        MedicalRecord record = MedicalRecord.fromJSONString(new String(recordBytes));

        // Update IPFS reference
        record.setIpfsCid(newIpfsCid);
        record.setFileHash(newFileHash);

        // Save updated record
        stub.putState(recordId, record.toJSONString().getBytes());

        // Emit event
        stub.setEvent("RecordUpdated", record.toJSONString().getBytes());

        System.out.println("Record updated: " + recordId);
        return record.toJSONString();
    }

    /**
     * Mark record as deleted (soft delete)
     *
     * @param ctx      Context
     * @param recordId Record identifier
     * @return Success message
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String deleteRecord(final Context ctx, String recordId) {
        ChaincodeStub stub = ctx.getStub();

        // Get existing record
        byte[] recordBytes = stub.getState(recordId);
        if (recordBytes == null || recordBytes.length == 0) {
            throw new RuntimeException("Record " + recordId + " does not exist");
        }

        MedicalRecord record = MedicalRecord.fromJSONString(new String(recordBytes));

        // Mark as deleted (soft delete for audit trail)
        record.setStatus("DELETED");

        // Save updated record
        stub.putState(recordId, record.toJSONString().getBytes());

        // Emit event
        stub.setEvent("RecordDeleted", recordId.getBytes());

        System.out.println("Record deleted: " + recordId);
        return "{\"message\":\"Record deleted successfully\",\"recordId\":\"" + recordId + "\"}";
    }

    /**
     * Get all records (admin function)
     *
     * @param ctx Context
     * @return List of all MedicalRecords as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAllRecords(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<MedicalRecord> records = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result : results) {
            // Filter to only include actual records (not other types)
            try {
                MedicalRecord record = MedicalRecord.fromJSONString(result.getStringValue());
                if (record.getRecordId() != null) {
                    records.add(record);
                }
            } catch (Exception e) {
                // Skip non-record entries
            }
        }

        return gson.toJson(records);
    }

    /**
     * Query all MEDICAL_RECORD entries via CouchDB rich query (precise docType
     * filter)
     * Used by AdminController to produce accurate record counts.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryAllMedicalRecords(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = "{\"selector\":{\"docType\":\"MEDICAL_RECORD\"},\"sort\":[{\"timestamp\":\"desc\"}]}";

        List<MedicalRecord> records = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            try {
                MedicalRecord record = MedicalRecord.fromJSONString(result.getStringValue());
                records.add(record);
            } catch (Exception e) {
                // Skip malformed entries
            }
        }

        return gson.toJson(records);
    }

    /**
     * Check if user has access to record
     * Helper method used by other contracts
     *
     * @param ctx      Context
     * @param userId   User identifier
     * @param recordId Record identifier
     * @return true if user has access
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean hasAccess(final Context ctx, String userId, String recordId) {
        ChaincodeStub stub = ctx.getStub();

        byte[] recordBytes = stub.getState(recordId);
        if (recordBytes == null || recordBytes.length == 0) {
            return false;
        }

        MedicalRecord record = MedicalRecord.fromJSONString(new String(recordBytes));

        if (record.getPatientId().equals(userId)) {
            return true;
        }

        // Check allowedRoles derived from ABE policy
        String allowedRoles = record.getAllowedRoles();
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            for (String role : allowedRoles.split(",")) {
                if (role.trim().equalsIgnoreCase(userId)) {
                    return true;
                }
            }
        }

        return false;
    }
}
