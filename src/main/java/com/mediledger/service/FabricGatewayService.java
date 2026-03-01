package com.mediledger.service;

import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * Fabric Gateway Service
 * Connects to the live Hyperledger Fabric network and invokes chaincode
 */
@Service
public class FabricGatewayService {

    @Value("${fabric.organization.peer.url:localhost:7051}")
    private String peerUrl;

    @Value("${fabric.organization.msp-id:HealthcareOrgMSP}")
    private String mspId;

    @Value("${fabric.network.channel:healthcarechannel}")
    private String channelName;

    @Value("${fabric.network.chaincode:mediledger-cc}")
    private String chaincodeName;

    @Value("${fabric.wallet.path:wallet}")
    private String walletPath;

    private Gateway gateway;
    private ManagedChannel grpcChannel;
    private Network network;
    private Contract contract;

    private static final String ADMIN_USER = "admin";

    @PostConstruct
    public void init() {
        try {
            Path certPath = findAdminCert();
            Path keyPath = findAdminKey();

            if (certPath == null || keyPath == null) {
                System.out.println("⚠️  FabricGatewayService: admin cert/key not found — running in simulation mode");
                return;
            }

            java.security.cert.X509Certificate certificate = Identities
                    .readX509Certificate(Files.newBufferedReader(certPath));
            java.security.PrivateKey privateKey = Identities.readPrivateKey(Files.newBufferedReader(keyPath));

            X509Identity identity = new X509Identity(mspId, certificate);
            Signer signer = Signers.newPrivateKeySigner(privateKey);

            // Connect to peer via gRPC (no TLS for dev network)
            grpcChannel = NettyChannelBuilder.forTarget("localhost:7051")
                    .usePlaintext()
                    .build();

            gateway = Gateway.newInstance()
                    .identity(identity)
                    .signer(signer)
                    .connection(grpcChannel)
                    .evaluateOptions(options -> options.withDeadlineAfter(30, TimeUnit.SECONDS))
                    .submitOptions(options -> options.withDeadlineAfter(30, TimeUnit.SECONDS))
                    .connect();

            network = gateway.getNetwork(channelName);
            contract = network.getContract(chaincodeName);

            System.out.println("✅ FabricGatewayService connected to " + channelName + "/" + chaincodeName);

        } catch (Exception e) {
            System.out.println(
                    "⚠️  FabricGatewayService init failed: " + e.getMessage() + " — running in simulation mode");
            gateway = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (gateway != null) {
            try {
                gateway.close();
            } catch (Exception ignore) {
            }
        }
        if (grpcChannel != null) {
            grpcChannel.shutdown();
        }
    }

    public boolean isAvailable() {
        return contract != null;
    }

    /**
     * Submit a transaction (write to ledger) — uses default contract
     */
    public String submitTransaction(String functionName, String... args) throws GatewayException, CommitException {
        byte[] result = contract.submitTransaction(functionName, args);
        return new String(result);
    }

    /**
     * Submit a transaction on a specific named contract (e.g. "AuditTrail")
     */
    public String submitContractTransaction(String contractName, String functionName, String... args)
            throws GatewayException, CommitException {
        if (network == null)
            throw new IllegalStateException("Network not connected");
        Contract namedContract = network.getContract(chaincodeName, contractName);
        byte[] result = namedContract.submitTransaction(functionName, args);
        return new String(result);
    }

    /**
     * Evaluate a transaction (read from ledger, no commit) — uses default contract
     */
    public String evaluateTransaction(String functionName, String... args) throws GatewayException {
        byte[] result = contract.evaluateTransaction(functionName, args);
        return new String(result);
    }

    /**
     * Evaluate a transaction on a specific named contract
     */
    public String evaluateContractTransaction(String contractName, String functionName, String... args)
            throws GatewayException {
        if (network == null)
            throw new IllegalStateException("Network not connected");
        Contract namedContract = network.getContract(chaincodeName, contractName);
        byte[] result = namedContract.evaluateTransaction(functionName, args);
        return new String(result);
    }

    // ─── Helpers to locate admin identity from wallet directory ──────────

    private Path findAdminCert() {
        String[] candidates = {
                walletPath + "/" + ADMIN_USER + "/signcerts/cert.pem",
                walletPath + "/" + ADMIN_USER + ".id",
                "fabric-network/crypto-config/peerOrganizations/healthcare.mediledger.com/users/Admin@healthcare.mediledger.com/msp/signcerts/Admin@healthcare.mediledger.com-cert.pem"
        };
        for (String p : candidates) {
            Path path = Paths.get(p);
            if (Files.exists(path) && p.endsWith(".pem"))
                return path;
        }
        // Search wallet directory
        try {
            Path wPath = Paths.get(walletPath);
            if (Files.notExists(wPath))
                return null;
            return Files.walk(wPath)
                    .filter(p -> p.toString().endsWith("cert.pem") || p.toString().endsWith("-cert.pem"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Path findAdminKey() {
        try {
            // Check wallet
            Path wPath = Paths.get(walletPath);
            if (Files.exists(wPath)) {
                Path key = Files.walk(wPath)
                        .filter(p -> p.toString().endsWith("_sk") || p.toString().endsWith("priv_sk")
                                || p.toString().endsWith("key.pem"))
                        .findFirst().orElse(null);
                if (key != null)
                    return key;
            }
            // Check crypto-config
            Path ccPath = Paths.get(
                    "fabric-network/crypto-config/peerOrganizations/healthcare.mediledger.com/users/Admin@healthcare.mediledger.com/msp/keystore");
            if (Files.exists(ccPath)) {
                return Files.walk(ccPath).filter(p -> !Files.isDirectory(p)).findFirst().orElse(null);
            }
        } catch (IOException e) {
            /* ignore */ }
        return null;
    }
}
