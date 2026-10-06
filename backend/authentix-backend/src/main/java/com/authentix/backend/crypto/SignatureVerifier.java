package com.authentix.backend.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Optional;

public final class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);

    private SignatureVerifier() {
    }

    /**
     * Recovers the EVM address from an EIP-191 personal_sign signature and original message.
     *
     * @param message      the exact message string that was signed
     * @param signatureHex the hex-encoded signature (e.g. "0x...")
     * @return Optional containing the recovered EIP-55 checksum address, or empty if recovery fails
     */
    public static Optional<String> recoverAddress(String message, String signatureHex) {
        if (message == null || signatureHex == null || signatureHex.isBlank()) {
            return Optional.empty();
        }

        try {
            Sign.SignatureData signatureData = Sign.signatureDataFromHex(signatureHex.trim());
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);

            if (publicKey == null) {
                return Optional.empty();
            }

            String rawAddress = Keys.getAddress(publicKey);
            String checksumAddress = Keys.toChecksumAddress(rawAddress);
            return Optional.of(checksumAddress);
        } catch (SignatureException | IllegalArgumentException | ArithmeticException ex) {
            log.debug("Signature recovery failed: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error during signature recovery: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Verifies that the EIP-191 personal_sign signature for the message was signed by claimedAddress.
     *
     * @param message        the exact message string that was signed
     * @param signatureHex   the hex-encoded signature
     * @param claimedAddress the expected signer wallet address
     * @return true if the recovered address matches claimedAddress (case-insensitively), false otherwise
     */
    public static boolean verify(String message, String signatureHex, String claimedAddress) {
        if (claimedAddress == null || claimedAddress.isBlank()) {
            return false;
        }

        return recoverAddress(message, signatureHex)
                .map(recovered -> recovered.equalsIgnoreCase(claimedAddress.trim()))
                .orElse(false);
    }
}
