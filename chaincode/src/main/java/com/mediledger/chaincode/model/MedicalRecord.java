package com.mediledger.chaincode.model;

import com.google.gson.Gson;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.Objects;

/**
 * Medical Record Model
 * Stores metadata about medical records on the blockchain
 * Actual encrypted files stored on IPFS
 */
@DataType()
public class MedicalRecord {

    @Property()
    private String recordId;

    @Property()
    private String patientId;

    @Property()
    private String ipfsCid; // IPFS Content Identifier

    @Property()
    private String fileHash; // SHA-256 hash for integrity verification

    @Property()
    private String uploadedBy; // Username of uploader (doctor/patient)

    @Property()
    private String timestamp; // ISO 8601 timestamp

    @Property()
    private String recordType; // e.g., "Lab Report", "X-Ray", "Prescription"

    @Property()
    private String department; // e.g., "Cardiology", "Neurology"

    @Property()
    private String status; // "ACTIVE", "ARCHIVED", "DELETED"

    private static final Gson gson = new Gson();

    public MedicalRecord() {
    }

    public MedicalRecord(String recordId, String patientId, String ipfsCid,
            String fileHash, String uploadedBy, String timestamp,
            String recordType, String department) {
        this.recordId = recordId;
        this.patientId = patientId;
        this.ipfsCid = ipfsCid;
        this.fileHash = fileHash;
        this.uploadedBy = uploadedBy;
        this.timestamp = timestamp;
        this.recordType = recordType;
        this.department = department;
        this.status = "ACTIVE";
    }

    // Getters and Setters
    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getIpfsCid() {
        return ipfsCid;
    }

    public void setIpfsCid(String ipfsCid) {
        this.ipfsCid = ipfsCid;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Serialization methods
    public String toJSONString() {
        return gson.toJson(this);
    }

    public static MedicalRecord fromJSONString(String json) {
        return gson.fromJson(json, MedicalRecord.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MedicalRecord that = (MedicalRecord) o;
        return Objects.equals(recordId, that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
