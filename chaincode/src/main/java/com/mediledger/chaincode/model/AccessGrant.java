package com.mediledger.chaincode.model;

import com.google.gson.Gson;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import java.util.Objects;

/**
 * Access Grant Model
 * Represents consent given by a patient to a doctor for accessing medical
 * records
 */
@DataType()
public class AccessGrant {

    @Property()
    private String grantId;

    @Property()
    private String patientId;

    @Property()
    private String doctorId;

    @Property()
    private String recordId; // Specific record or "ALL" for all records

    @Property()
    private String grantedAt; // ISO 8601 timestamp

    @Property()
    private String expiresAt; // ISO 8601 timestamp, null for no expiration

    @Property()
    private String status; // "ACTIVE", "REVOKED", "EXPIRED"

    @Property()
    private String grantedBy; // patientId (for audit)

    @Property()
    private String purpose; // Reason for access (HIPAA requirement)

    private static final Gson gson = new Gson();

    public AccessGrant() {
    }

    public AccessGrant(String grantId, String patientId, String doctorId,
            String recordId, String grantedAt, String expiresAt,
            String purpose) {
        this.grantId = grantId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.recordId = recordId;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
        this.grantedBy = patientId;
        this.purpose = purpose;
    }

    // Getters and Setters
    public String getGrantId() {
        return grantId;
    }

    public void setGrantId(String grantId) {
        this.grantId = grantId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(String grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String toJSONString() {
        return gson.toJson(this);
    }

    public static AccessGrant fromJSONString(String json) {
        return gson.fromJson(json, AccessGrant.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AccessGrant that = (AccessGrant) o;
        return Objects.equals(grantId, that.grantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantId);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
