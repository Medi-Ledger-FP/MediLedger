package com.mediledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for MediLedger Healthcare Blockchain Wallet
 * Built with Spring Boot 3 and Hyperledger Fabric
 */
@SpringBootApplication
public class MediLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediLedgerApplication.class, args);
    }
}
