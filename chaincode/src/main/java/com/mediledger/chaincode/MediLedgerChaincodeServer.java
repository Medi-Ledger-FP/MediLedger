package com.mediledger.chaincode;

import org.hyperledger.fabric.contract.ContractRouter;
import org.hyperledger.fabric.shim.ChaincodeServerProperties;
import org.hyperledger.fabric.shim.NettyChaincodeServer;

import java.net.InetSocketAddress;

/**
 * Custom CaaS (Chaincode-as-a-Service) launcher.
 *
 * Fixes two issues with the default ContractRouter:
 * 1. MaxConnectionAgeSeconds: raised from 5s → 3600s so the peer gRPC
 * connection is never killed mid-transaction.
 * 2. Explicit server address from CHAINCODE_SERVER_ADDRESS env var.
 */
public class MediLedgerChaincodeServer {

    public static void main(String[] args) throws Exception {
        // -- Server properties ------------------------------------------------
        ChaincodeServerProperties props = new ChaincodeServerProperties();

        // Fix 1: 3600s max connection age (default was 5s → killed every 5 seconds)
        props.setMaxConnectionAgeSeconds(3600);
        props.setKeepAliveTimeMinutes(5);
        props.setKeepAliveTimeoutSeconds(20);
        props.setPermitKeepAliveWithoutCalls(true);

        // Fix 2: explicit server address from environment
        String serverAddr = System.getenv() != null
                ? System.getenv().getOrDefault("CHAINCODE_SERVER_ADDRESS", "0.0.0.0:9999")
                : "0.0.0.0:9999";
        int lastColon = serverAddr.lastIndexOf(':');
        String bindHost = serverAddr.substring(0, lastColon);
        int bindPort = Integer.parseInt(serverAddr.substring(lastColon + 1));
        props.setServerAddress(new InetSocketAddress(bindHost, bindPort));

        System.out.println("MediLedger CaaS server starting on " + serverAddr
                + " | maxConnectionAge=" + props.getMaxConnectionAgeSeconds() + "s");

        // -- Start chaincode server -------------------------------------------
        ContractRouter router = new ContractRouter(args);
        new NettyChaincodeServer(router, props).start();
    }
}
