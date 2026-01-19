package com.mediledger.config;

import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration for Hyperledger Fabric SDK components
 */
@Configuration
public class FabricConfig {

    @Value("${fabric.ca.url}")
    private String caUrl;

    @Value("${fabric.ca.name}")
    private String caName;

    @Value("${fabric.wallet.path}")
    private String walletPath;

    @Value("${fabric.network.connection-profile}")
    private String connectionProfilePath;

    @Value("${fabric.organization.msp-id}")
    private String mspId;

    /**
     * Returns wallet directory path for storing identities
     */
    @Bean
    public Path walletPath() {
        return Paths.get(walletPath);
    }

    /**
     * Creates Hyperledger Fabric CA client for identity management
     */
    @Bean
    public HFCAClient caClient() throws Exception {
        Properties props = new Properties();
        props.put("pemFile", "");
        props.put("allowAllHostNames", "true");

        HFCAClient caClient = HFCAClient.createNewInstance(caName, caUrl, props);
        CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
        caClient.setCryptoSuite(cryptoSuite);

        return caClient;
    }

    /**
     * Creates HFClient for Fabric network operations
     */
    @Bean
    public HFClient hfClient() throws Exception {
        HFClient client = HFClient.createNewInstance();
        CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
        client.setCryptoSuite(cryptoSuite);
        return client;
    }

    public String getMspId() {
        return mspId;
    }
}
