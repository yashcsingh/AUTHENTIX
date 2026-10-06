package com.authentix.backend;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.protocol.Web3j;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class BlockchainConnectionTest {

    @Autowired
    private Web3j web3j;

    @Value("${blockchain.rpc-url:}")
    private String rpcUrl;

    @Test
    void testBlockchainConnection() throws Exception {
        Assumptions.assumeFalse(rpcUrl == null || rpcUrl.isBlank(), "Blockchain RPC is not configured for this environment");

        String clientVersion =
                web3j.web3ClientVersion()
                        .send()
                        .getWeb3ClientVersion();

        System.out.println("=================================");
        System.out.println("BLOCKCHAIN CONNECTION SUCCESS");
        System.out.println("Web3 Client: " + clientVersion);
        System.out.println("=================================");

        assertNotNull(clientVersion);
    }
}