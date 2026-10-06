package com.authentix.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class BlockchainConfig {

    @Value("${blockchain.rpc-url:http://127.0.0.1:8545}")
    private String rpcUrl;

    @Value("${blockchain.private-key:}")
    private String privateKey;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    @Lazy
    public Credentials credentials() {
        if (privateKey == null || privateKey.trim().isBlank()) {
            throw new IllegalStateException("Blockchain signing credentials are not configured. BLOCKCHAIN_PRIVATE_KEY is required for blockchain write operations.");
        }
        try {
            return Credentials.create(privateKey.trim());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid blockchain signing credentials configured in BLOCKCHAIN_PRIVATE_KEY.");
        }
    }
}