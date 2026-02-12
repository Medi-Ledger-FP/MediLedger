package com.mediledger.dto;

import java.time.Instant;

/**
 * Data Transfer Object for Medical Record
 */
public class RecordDTO {
    private String recordId;
    private String patientId;
    private String ipfsCid;
    private String fileHash;
    private String recordType;
    private String department;
    private String status;
    private String timestamp;

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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    // Request DTO for creating records
    public static class CreateRecordRequest {
        private String patientId;
        private String ipfsCid;
        private String fileHash;
        private String recordType;
        private String department;

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
    }

    // Response DTO
    public static class RecordResponse {
        private boolean success;
        private String message;
        private RecordDTO record;

        public RecordResponse(boolean success, String message, RecordDTO record) {
            this.success = success;
            this.message = message;
            this.record = record;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public RecordDTO getRecord() {
            return record;
        }
    }
}
