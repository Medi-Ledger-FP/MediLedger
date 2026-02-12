package com.mediledger.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Simple Policy-Based Access Control
 * Checks if a doctor/user has permission to access a resource
 */
@Service
public class AccessControlService {

    // In-memory policy store (in production, use database)
    private final Map<String, AccessPolicy> policies = new HashMap<>();

    /**
     * Grant access based on policy
     */
    public String grantAccess(String patientId, String doctorId, String recordId,
            String expiresAt, String purpose) {
        String policyId = UUID.randomUUID().toString().substring(0, 8);

        AccessPolicy policy = new AccessPolicy(
                policyId,
                patientId,
                doctorId,
                recordId,
                Instant.parse(expiresAt),
                purpose,
                true);

        policies.put(policyId, policy);
        return policyId;
    }

    /**
     * Check if doctor has access to record
     */
    public boolean checkAccess(String doctorId, String recordId, String patientId) {
        // Find matching policy
        for (AccessPolicy policy : policies.values()) {
            if (policy.doctorId.equals(doctorId) &&
                    policy.recordId.equals(recordId) &&
                    policy.patientId.equals(patientId) &&
                    policy.active &&
                    policy.expiresAt.isAfter(Instant.now())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Revoke access
     */
    public void revokeAccess(String policyId) {
        AccessPolicy policy = policies.get(policyId);
        if (policy != null) {
            policy.active = false;
        }
    }

    /**
     * List all authorized doctors for a patient
     */
    public List<String> listAuthorizedDoctors(String patientId) {
        Set<String> doctors = new HashSet<>();
        for (AccessPolicy policy : policies.values()) {
            if (policy.patientId.equals(patientId) &&
                    policy.active &&
                    policy.expiresAt.isAfter(Instant.now())) {
                doctors.add(policy.doctorId);
            }
        }
        return new ArrayList<>(doctors);
    }

    /**
     * List all patients a doctor has access to
     */
    public List<String> listDoctorAccess(String doctorId) {
        Set<String> patients = new HashSet<>();
        for (AccessPolicy policy : policies.values()) {
            if (policy.doctorId.equals(doctorId) &&
                    policy.active &&
                    policy.expiresAt.isAfter(Instant.now())) {
                patients.add(policy.patientId);
            }
        }
        return new ArrayList<>(patients);
    }

    // Inner class for access policy
    public static class AccessPolicy {
        public final String policyId;
        public final String patientId;
        public final String doctorId;
        public final String recordId;
        public final Instant expiresAt;
        public final String purpose;
        public boolean active;

        public AccessPolicy(String policyId, String patientId, String doctorId,
                String recordId, Instant expiresAt, String purpose, boolean active) {
            this.policyId = policyId;
            this.patientId = patientId;
            this.doctorId = doctorId;
            this.recordId = recordId;
            this.expiresAt = expiresAt;
            this.purpose = purpose;
            this.active = active;
        }
    }
}
