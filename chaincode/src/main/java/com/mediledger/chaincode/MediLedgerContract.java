package com.mediledger.chaincode;

import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;

/**
 * MediLedger Main Contract
 * Coordinator contract that combines RecordLedger, ConsentManager, and
 * AuditTrail
 * This is the main entry point for the chaincode
 */
@Contract(name = "MediLedgerContract")
@Default
public class MediLedgerContract extends RecordLedger {

    /**
     * This contract extends RecordLedger and provides access to all three
     * contracts:
     * - RecordLedger (inherited)
     * - ConsentManager (available via fabric network)
     * - AuditTrail (available via fabric network)
     * 
     * All three contracts are deployed together and can be invoked
     * through their respective contract names or through this main contract.
     */

    public MediLedgerContract() {
        super();
    }
}
