package com.mediledger.chaincode;

import com.google.gson.Gson;
import com.mediledger.chaincode.model.AuditEntry;
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
 * AuditTrail Contract
 * HIPAA-compliant audit logging for all medical record access
 * Immutable audit trail on blockchain
 */
@Contract(name = "AuditTrail", info = @Info(title = "Audit Trail", description = "HIPAA-compliant audit logging system", version = "1.0.0", license = @License(name = "Apache 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.html"), contact = @Contact(email = "contact@mediledger.com", name = "MediLedger Team")))
public class AuditTrail implements ContractInterface {

    private final Gson gson = new Gson();

    /**
     * Log an access attempt to a medical record
     * This function is called automatically by other contracts
     *
     * @param ctx       Context
     * @param auditId   Unique audit identifier
     * @param userId    User performing the action
     * @param userRole  User's role (PATIENT, DOCTOR, ADMIN)
     * @param action    Action performed (VIEW_RECORD, CREATE_RECORD, etc.)
     * @param recordId  Record being accessed
     * @param patientId Patient whose data is accessed
     * @param result    Result of action (SUCCESS, DENIED, ERROR)
     * @param ipAddress Client IP address
     * @param reason    Reason for access (optional)
     * @return Created AuditEntry as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String logAccess(final Context ctx, String auditId, String userId,
            String userRole, String action, String recordId,
            String patientId, String result, String ipAddress,
            String reason) {
        ChaincodeStub stub = ctx.getStub();

        // Create timestamp (immutable)
        String timestamp = Instant.now().toString();

        // Create audit entry
        AuditEntry entry = new AuditEntry(
                auditId, timestamp, userId, userRole,
                action, recordId, patientId, result,
                ipAddress, reason);

        // Save to ledger (immutable)
        stub.putState(auditId, entry.toJSONString().getBytes());

        // Create composite key for efficient querying
        // Format: audit~patientId~timestamp
        String compositeKey = stub.createCompositeKey("audit",
                patientId, timestamp).toString();
        stub.putState(compositeKey, auditId.getBytes());

        // Emit event
        stub.setEvent("AccessLogged", entry.toJSONString().getBytes());

        System.out.println("Audit logged: " + auditId + " - " + action + " by " + userId);
        return entry.toJSONString();
    }

    /**
     * Log access with additional details (JSON string)
     *
     * @param ctx       Context
     * @param auditId   Unique audit identifier
     * @param userId    User performing the action
     * @param userRole  User's role
     * @param action    Action performed
     * @param recordId  Record being accessed
     * @param patientId Patient whose data is accessed
     * @param result    Result of action
     * @param ipAddress Client IP address
     * @param reason    Reason for access
     * @param details   Additional context as JSON string
     * @return Created AuditEntry as JSON
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String logAccessWithDetails(final Context ctx, String auditId, String userId,
            String userRole, String action, String recordId,
            String patientId, String result, String ipAddress,
            String reason, String details) {
        ChaincodeStub stub = ctx.getStub();

        String timestamp = Instant.now().toString();

        AuditEntry entry = new AuditEntry(
                auditId, timestamp, userId, userRole,
                action, recordId, patientId, result,
                ipAddress, reason);
        entry.setDetails(details);

        stub.putState(auditId, entry.toJSONString().getBytes());

        String compositeKey = stub.createCompositeKey("audit",
                patientId, timestamp).toString();
        stub.putState(compositeKey, auditId.getBytes());

        stub.setEvent("AccessLogged", entry.toJSONString().getBytes());

        return entry.toJSONString();
    }

    /**
     * Query audit log for a specific patient within a time range
     *
     * @param ctx       Context
     * @param patientId Patient identifier
     * @param startDate Start timestamp (ISO 8601)
     * @param endDate   End timestamp (ISO 8601)
     * @return List of AuditEntries as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryAuditLog(final Context ctx, String patientId,
            String startDate, String endDate) {
        ChaincodeStub stub = ctx.getStub();

        // Build CouchDB query
        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\"," +
                        "\"timestamp\":{\"$gte\":\"%s\",\"$lte\":\"%s\"}}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                patientId, startDate, endDate);

        List<AuditEntry> entries = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            entries.add(entry);
        }

        return gson.toJson(entries);
    }

    /**
     * Query audit log by action type
     *
     * @param ctx       Context
     * @param action    Action type (e.g., "VIEW_RECORD")
     * @param startDate Start timestamp
     * @param endDate   End timestamp
     * @return List of AuditEntries as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryByAction(final Context ctx, String action,
            String startDate, String endDate) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = String.format(
                "{\"selector\":{\"action\":\"%s\"," +
                        "\"timestamp\":{\"$gte\":\"%s\",\"$lte\":\"%s\"}}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                action, startDate, endDate);

        List<AuditEntry> entries = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            entries.add(entry);
        }

        return gson.toJson(entries);
    }

    /**
     * Query audit log by user (doctor viewing history)
     *
     * @param ctx       Context
     * @param userId    User identifier
     * @param startDate Start timestamp
     * @param endDate   End timestamp
     * @return List of AuditEntries as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryByUser(final Context ctx, String userId,
            String startDate, String endDate) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = String.format(
                "{\"selector\":{\"userId\":\"%s\"," +
                        "\"timestamp\":{\"$gte\":\"%s\",\"$lte\":\"%s\"}}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                userId, startDate, endDate);

        List<AuditEntry> entries = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            entries.add(entry);
        }

        return gson.toJson(entries);
    }

    /**
     * Get specific audit entry
     *
     * @param ctx     Context
     * @param auditId Audit identifier
     * @return AuditEntry as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAuditEntry(final Context ctx, String auditId) {
        ChaincodeStub stub = ctx.getStub();

        byte[] entryBytes = stub.getState(auditId);
        if (entryBytes == null || entryBytes.length == 0) {
            throw new RuntimeException("Audit entry " + auditId + " does not exist");
        }

        return new String(entryBytes);
    }

    /**
     * Query failed access attempts (security monitoring)
     *
     * @param ctx       Context
     * @param startDate Start timestamp
     * @param endDate   End timestamp
     * @return List of failed AuditEntries as JSON array
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String queryFailedAccess(final Context ctx, String startDate,
            String endDate) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = String.format(
                "{\"selector\":{\"result\":{\"$in\":[\"DENIED\",\"ERROR\"]}," +
                        "\"timestamp\":{\"$gte\":\"%s\",\"$lte\":\"%s\"}}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                startDate, endDate);

        List<AuditEntry> entries = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            entries.add(entry);
        }

        return gson.toJson(entries);
    }

    /**
     * Generate compliance report for a patient
     * Returns all access to patient's records
     *
     * @param ctx       Context
     * @param patientId Patient identifier
     * @return Compliance report as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String generateComplianceReport(final Context ctx, String patientId) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\"}," +
                        "\"sort\":[{\"timestamp\":\"desc\"}]}",
                patientId);

        List<AuditEntry> entries = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            entries.add(entry);
        }

        // Create compliance report
        String report = gson.toJson(new Object() {
            public final String patientId = patientId;
            public final String generatedAt = Instant.now().toString();
            public final int totalAccess = entries.size();
            public final List<AuditEntry> auditTrail = entries;
        });

        return report;
    }

    /**
     * Get audit statistics for a patient (summary)
     *
     * @param ctx       Context
     * @param patientId Patient identifier
     * @return Statistics as JSON
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getAuditStatistics(final Context ctx, String patientId) {
        ChaincodeStub stub = ctx.getStub();

        String queryString = String.format(
                "{\"selector\":{\"patientId\":\"%s\"}}",
                patientId);

        int totalAccess = 0;
        int successfulAccess = 0;
        int deniedAccess = 0;

        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);

        for (KeyValue result : results) {
            AuditEntry entry = AuditEntry.fromJSONString(result.getStringValue());
            totalAccess++;

            if ("SUCCESS".equals(entry.getResult())) {
                successfulAccess++;
            } else if ("DENIED".equals(entry.getResult())) {
                deniedAccess++;
            }
        }

        String stats = gson.toJson(new Object() {
            public final String patientId = patientId;
            public final int total = totalAccess;
            public final int successful = successfulAccess;
            public final int denied = deniedAccess;
            public final String generatedAt = Instant.now().toString();
        });

        return stats;
    }
}
