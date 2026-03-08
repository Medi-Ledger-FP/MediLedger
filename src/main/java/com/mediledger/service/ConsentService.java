package com.mediledger.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consent Service
 * Manages patient consent and doctor access
 */
@Service
public class ConsentService {

    private final AccessControlService accessControlService;

    public ConsentService(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public String grantAccess(String patientId, String doctorId, String recordId,
            String expiresAt, String purpose) throws Exception {
        return accessControlService.grantAccess(patientId, doctorId, recordId, expiresAt, purpose);
    }

    public void revokeAccess(String grantId) throws Exception {
        accessControlService.revokeAccess(grantId);
    }

    public boolean checkAccess(String doctorId, String recordId, String patientId) {
        return accessControlService.checkAccess(doctorId, recordId, patientId);
    }

    public String listAuthorizedDoctors(String patientId) {
        List<String> doctors = accessControlService.listAuthorizedDoctors(patientId);
        return doctors.toString();
    }

    public java.util.List<java.util.Map<String, String>> listAuthorizedGrants(String patientId) {
        return accessControlService.listAuthorizedGrants(patientId);
    }

    public String listDoctorAccess(String doctorId) {
        List<String> patients = accessControlService.listDoctorAccess(doctorId);
        return patients.toString();
    }

    public void updateExpiration(String grantId, String newExpiresAt) {
        System.out.println("Updating expiration for grant: " + grantId + " to " + newExpiresAt);
    }
}
