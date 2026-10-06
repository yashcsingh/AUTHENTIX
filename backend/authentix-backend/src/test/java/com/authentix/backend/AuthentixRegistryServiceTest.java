package com.authentix.backend;

import com.authentix.backend.service.AuthentixRegistryService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.abi.datatypes.Type;

import java.util.List;

@SpringBootTest
class AuthentixRegistryServiceTest {

    @Autowired
    private AuthentixRegistryService authentixRegistryService;

    @Value("${blockchain.rpc-url:}")
    private String rpcUrl;

    @Test
    void readAssetFromBlockchain() throws Exception {
        Assumptions.assumeFalse(rpcUrl == null || rpcUrl.isBlank(), "Blockchain RPC is not configured for this environment");

        List<Type> asset =
                authentixRegistryService.getAsset(
                        "PSH-KSH-2026-000001"
                );

        System.out.println("=================================");
        System.out.println("AUTHENTIX BLOCKCHAIN ASSET");
        System.out.println("=================================");

        for (int i = 0; i < asset.size(); i++) {
            System.out.println(i + ": " + asset.get(i).getValue());
        }

        System.out.println("=================================");
    }
}