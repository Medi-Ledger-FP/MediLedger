package com.mediledger.chaincode.model;

import com.google.gson.Gson;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.Objects;

/**
 * Audit Entry Model
 * HIPAA-compliant audit trail for all access to medical records
 */
@DataType()
public class AuditEntry {

    @Property()
    private String auditId;

    @Property()
    private String timestamp; // ISO 8601 timestamp

    @Property()
    private String userId; // Who performed the action

    @Property()
    private String userRole; // PATIENT, DOCTOR, ADMIN

    @Property()
    private String action; // VIEW_RECORD, CREATE_RECORD, GRANT_ACCESS, etc.

    @Property()
    private String recordId; // Which record was accessed

    @Property()
    private String patientId; // Which patient's data

    @Property()
    private String result; // SUCCESS, DENIED, ERROR

    @Property()
    private String ipAddress; // Client IP address

    @Property()
    private String reason; // Reason for access (optional)

    @Property()
    private String details; // Additional context (JSON string)

    private static final Gson gson = new Gson();

    public AuditEntry() {
    }

    public AuditEntry(String auditId, String timestamp, String userId,
            String userRole, String action, String recordId,
            String patientId, String result, String ipAddress,
            String reason) {
        this.auditId = auditId;
        this.timestamp = timestamp;
        this.userId = userId;
        this.userRole = userRole;
        this.action = action;
        this.recordId = recordId;
        this.patientId = patientId;
        this.result = result;
        this.ipAddress = ipAddress;
        this.reason = reason;
    }

    // Getters and Setters
    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String toJSONString() {
        return gson.toJson(this);
    }

    public static AuditEntry fromJSONString(String json) {
        return gson.fromJson(json, AuditEntry.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AuditEntry that = (AuditEntry) o;
        return Objects.equals(auditId, that.auditId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditId);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
