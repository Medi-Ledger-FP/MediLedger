package com.mediledger.dto;

/**
 * Data Transfer Object for Consent/Access Grant
 */
public class ConsentDTO {
    private String grantId;
    private String patientId;
    private String doctorId;
    private String recordId;
    private String expiresAt;
    private String purpose;
    private String status;
    private String timestamp;

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

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    // Request DTO for granting access
    public static class GrantAccessRequest {
        private String patientId;
        private String doctorId;
        private String recordId;
        private String expiresAt;
        private String purpose;

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

        public String getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(String expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }
    }

    // Response DTO
    public static class ConsentResponse {
        private boolean success;
        private String message;
        private ConsentDTO consent;

        public ConsentResponse(boolean success, String message, ConsentDTO consent) {
            this.success = success;
            this.message = message;
            this.consent = consent;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public ConsentDTO getConsent() {
            return consent;
        }
    }
}
