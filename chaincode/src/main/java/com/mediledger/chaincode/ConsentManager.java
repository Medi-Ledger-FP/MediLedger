package com.mediledger.chaincode;

import com.google.gson.Gson;
import com.mediledger.chaincode.model.AccessGrant;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ConsentManager Contract
 * Manages patient consent for doctor access to medical records
 * HIPAA-compliant access control system
 */
@Contract(name = "ConsentManager", info = @Info(title = "Consent Manager", description = "Manages patient consent and access control", version = "1.0.0", license = @License(name = "Apache 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.html"), contact = @Contact(email = "contact@mediledger.com", name = "MediLedger Team")))
public class ConsentManager implements ContractInterface {

    private final Gson gson = new Gson();

    /**
     * Grant access to a doctor for a specific record or all records
     *
     * @param ctx       Context
     * @param grantId   Unique grant identifier
     * @param patientId Patient granting access
     * @param doctorId  Doctor receiving access
     * @param recordId  Specific record ID or "ALL"
     * @param expiresAt Expiration timestamp (ISO 8601) or "NEVER"
     * @param purpose   Reason for access (HIPAA requirement)
     * @return Created AccessGrant as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String grantAccess(final Context ctx, String grantId, String patientId,
            String doctorId, String recordId, String expiresAt,
            String purpose) {
        ChaincodeStub stub = ctx.getStub();

        // Verify caller is the patient
        String callerId = ctx.getClientIdentity().getId();
        if (!callerId.contains(patientId)) {
            throw new RuntimeException("Only patient can grant access to their records");
        }

        // Check if grant already exists
        byte[] existingGrant = stub.getState(grantId);
        if (existingGrant != null && existingGrant.length > 0) {
            throw new RuntimeException("Grant " + grantId + " already exists");
        }

        // Create timestamp
        String grantedAt = Instant.now().toString();

        // Handle "NEVER" expiration
        String finalExpiresAt = expiresAt.equals("NEVER") ? null : expiresAt;

        // Create access grant
        AccessGrant grant = new AccessGrant(
                grantId, patientId, doctorId, recordId,
                grantedAt, finalExpiresAt, purpose);

        // Save to ledger
        stub.putState(grantId, grant.toJSONString().getBytes());

        // Create composite key for efficient querying
        String compositeKey = stub.createCompositeKey("grant",
                patientId, doctorId, recordId).toString();
        stub.putState(compositeKey, grantId.getBytes());

        // Emit event
        stub.setEvent("AccessGranted", grant.toJSONString().getBytes());

        System.out.println("Access granted: " + grantId);
        return grant.toJSONString();
    }

    /**
     * Revoke previously granted access
     *
     * @param ctx     Context
     * @param grantId Grant identifier to revoke
     * @return Success message
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String revokeAccess(final Context ctx, String grantId) {
        ChaincodeStub stub = ctx.getStub();

        // Get existing grant
        byte[] grantBytes = stub.getState(grantId);
        if (grantBytes == null || grantBytes.length == 0) {
            throw new RuntimeException("Grant " + grantId + " does not exist");
        }

        AccessGrant grant = AccessGrant.fromJSONString(new String(grantBytes));

        // Verify caller is the patient who granted access
        String callerId = ctx.getClientIdentity().getId();
        if (!callerId.contains(grant.getPatientId())) {
            throw new RuntimeException("Only patient can revoke access");
        }

        // Update status to REVOKED
        grant.setStatus("REVOKED");

        // Save updated grant
        stub.putState(grantId, grant.toJSONString().getBytes());

        // Emit event
        stub.setEvent("AccessRevoked", grantId.getBytes());

        System.out.println("Access revoked: " + grantId);
        return "{\"message\":\"Access revoked successfully\",\"grantId\":\"" + grantId + "\"}";
    }

    /**
     * Check if a doctor has access to a specific record
     *
     * @param ctx       Context
     * @param doctorId  Doctor identifier
     * @param recordId  Record identifier
     * @param patientId Patient identifier
     * @return true if doctor has valid access
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean checkAccess(final Context ctx, String doctorId,
            String recordId, String patientId) {
        ChaincodeStub stub = ctx.getStub();

        // Query for grants
        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\",\"doctorId\":\"%s\"," +
                        "\"$or\":[{\"recordId\":\"%s\"},{\"recordId\":\"ALL\"}]," +
                        "\"status\":\"ACTIVE\"}}",
                patientId, doctorId, recordId);

        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        // Check if any active grants exist
        for (KeyValue result : results) {
            AccessGrant grant = AccessGrant.fromJSONString(result.getStringValue());

            // Check expiration
            if (grant.getExpiresAt() != null) {
                ZonedDateTime expiration = ZonedDateTime.parse(grant.getExpiresAt(),
                        DateTimeFormatter.ISO_DATE_TIME);
                if (ZonedDateTime.now().isAfter(expiration)) {
                    continue; // Grant expired
                }
            }

            // Valid grant found
            return true;
        }

        return false;
    }

    /**
     * List all doctors who have access to a patient's records
     *
     * @param ctx       Context
     * @param patientId Patient identifier
     * @return List of AccessGrants as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String listAuthorizedDoctors(final Context ctx, String patientId) {
        ChaincodeStub stub = ctx.getStub();

        // Query for all active grants for this patient
        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\",\"status\":\"ACTIVE\"}," +
                        "\"sort\":[{\"grantedAt\":\"desc\"}]}",
                patientId);

        List<AccessGrant> grants = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AccessGrant grant = AccessGrant.fromJSONString(result.getStringValue());
            grants.add(grant);
        }

        return gson.toJson(grants);
    }

    /**
     * List all patients a doctor has access to
     *
     * @param ctx      Context
     * @param doctorId Doctor identifier
     * @return List of AccessGrants as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String listDoctorAccess(final Context ctx, String doctorId) {
        ChaincodeStub stub = ctx.getStub();

        // Query for all active grants for this doctor
        String queryString = String.format(
                "{\"selector\":{\"doctorId\":\"%s\",\"status\":\"ACTIVE\"}," +
                        "\"sort\":[{\"grantedAt\":\"desc\"}]}",
                doctorId);

        List<AccessGrant> grants = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AccessGrant grant = AccessGrant.fromJSONString(result.getStringValue());
            grants.add(grant);
        }

        return gson.toJson(grants);
    }

    /**
     * Get specific grant details
     *
     * @param ctx     Context
     * @param grantId Grant identifier
     * @return AccessGrant as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getGrant(final Context ctx, String grantId) {
        ChaincodeStub stub = ctx.getStub();

        byte[] grantBytes = stub.getState(grantId);
        if (grantBytes == null || grantBytes.length == 0) {
            throw new RuntimeException("Grant " + grantId + " does not exist");
        }

        return new String(grantBytes);
    }

    /**
     * Update grant expiration date
     *
     * @param ctx          Context
     * @param grantId      Grant identifier
     * @param newExpiresAt New expiration timestamp
     * @return Updated AccessGrant as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String updateExpiration(final Context ctx, String grantId,
            String newExpiresAt) {
        ChaincodeStub stub = ctx.getStub();

        // Get existing grant
        byte[] grantBytes = stub.getState(grantId);
        if (grantBytes == null || grantBytes.length == 0) {
            throw new RuntimeException("Grant " + grantId + " does not exist");
        }

        AccessGrant grant = AccessGrant.fromJSONString(new String(grantBytes));

        // Verify caller is the patient
        String callerId = ctx.getClientIdentity().getId();
        if (!callerId.contains(grant.getPatientId())) {
            throw new RuntimeException("Only patient can update expiration");
        }

        // Update expiration
        grant.setExpiresAt(newExpiresAt.equals("NEVER") ? null : newExpiresAt);

        // Save updated grant
        stub.putState(grantId, grant.toJSONString().getBytes());

        // Emit event
        stub.setEvent("ExpirationUpdated", grant.toJSONString().getBytes());

        System.out.println("Grant expiration updated: " + grantId);
        return grant.toJSONString();
    }

    /**
     * Cleanup expired grants (admin function)
     *
     * @param ctx Context
     * @return Number of grants updated
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String cleanupExpiredGrants(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        // Query for all active grants with expiration
        String queryString = "{\"selector\":{\"status\":\"ACTIVE\"," +
                "\"expiresAt\":{\"$ne\":null}}}";

        int count = 0;
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);
        String now = Instant.now().toString();

        for (KeyValue result : results) {
            AccessGrant grant = AccessGrant.fromJSONString(result.getStringValue());

            // Check if expired
            if (grant.getExpiresAt() != null &&
                    grant.getExpiresAt().compareTo(now) < 0) {
                grant.setStatus("EXPIRED");
                stub.putState(grant.getGrantId(), grant.toJSONString().getBytes());
                count++;
            }
        }

        String message = String.format("{\"message\":\"Cleaned up %d expired grants\"," +
                "\"count\":%d}", count, count);
        System.out.println("Expired grants cleaned up: " + count);
        return message;
    }
}
