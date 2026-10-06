package com.authentix.backend;

import com.authentix.backend.config.BlockchainConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Numeric;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BlockchainCredentialConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Web3j web3j;

    @Autowired
    private BlockchainConfig blockchainConfig;

    @Test
    @DisplayName("1. Application context and Web3j bean load cleanly without BLOCKCHAIN_PRIVATE_KEY")
    void testContextAndWeb3jInitializeWithoutPrivateKey() {
        assertNotNull(applicationContext, "Application context must load successfully without private key");
        assertNotNull(web3j, "Web3j client bean must initialize without private key");
        assertNotNull(blockchainConfig, "BlockchainConfig must initialize without private key");
    }

    @Test
    @DisplayName("2. Invoking credentials bean without configuration fails safely with IllegalStateException")
    void testCredentialsBeanFailsSafelyWhenUnconfigured() {
        BlockchainConfig config = new BlockchainConfig();
        ReflectionTestUtils.setField(config, "rpcUrl", "http://127.0.0.1:8545");
        ReflectionTestUtils.setField(config, "privateKey", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::credentials);

        assertTrue(ex.getMessage().contains("Blockchain signing credentials are not configured"));
        assertTrue(ex.getMessage().contains("BLOCKCHAIN_PRIVATE_KEY is required for blockchain write operations"));
        // Ensure no internal memory addresses, passwords, or secrets leaked
        assertFalse(ex.getMessage().contains("password"));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    @DisplayName("3. Invoking credentials bean with malformed key fails safely without echoing input")
    void testCredentialsBeanFailsSafelyWithMalformedKey() {
        String invalidKeyInput = "malformed_key_12345_not_hex";
        BlockchainConfig config = new BlockchainConfig();
        ReflectionTestUtils.setField(config, "rpcUrl", "http://127.0.0.1:8545");
        ReflectionTestUtils.setField(config, "privateKey", invalidKeyInput);

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::credentials);

        assertTrue(ex.getMessage().contains("Invalid blockchain signing credentials"));
        // The error message must NOT echo the client/environment input value
        assertFalse(ex.getMessage().contains(invalidKeyInput));
    }

    @Test
    @DisplayName("4. Credentials bean successfully instantiates with a runtime ephemeral key")
    void testCredentialsInstantiatesWithRuntimeEphemeralKey() throws Exception {
        // Generate an ephemeral keypair in test memory only - never hardcoded, never committed
        ECKeyPair ephemeralKeyPair = Keys.createEcKeyPair();
        String ephemeralPrivateKeyHex = Numeric.toHexStringWithPrefix(ephemeralKeyPair.getPrivateKey());
        String expectedAddress = Keys.toChecksumAddress(Keys.getAddress(ephemeralKeyPair));

        BlockchainConfig config = new BlockchainConfig();
        ReflectionTestUtils.setField(config, "rpcUrl", "http://127.0.0.1:8545");
        ReflectionTestUtils.setField(config, "privateKey", ephemeralPrivateKeyHex);

        Credentials credentials = config.credentials();
        assertNotNull(credentials);
        assertEquals(expectedAddress.toLowerCase(), credentials.getAddress().toLowerCase());
    }
}
