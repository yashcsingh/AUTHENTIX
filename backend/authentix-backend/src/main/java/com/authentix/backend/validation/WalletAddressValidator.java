package com.authentix.backend.validation;

import com.authentix.backend.exception.BadRequestException;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;

import java.util.regex.Pattern;

public final class WalletAddressValidator {

    private static final Pattern EVM_HEX_PATTERN = Pattern.compile("^0[xX][0-9a-fA-F]{40}$");

    private WalletAddressValidator() {
    }

    /**
     * Validates an EVM wallet address.
     *
     * @param address raw address string
     * @throws BadRequestException if address is null, blank, has wrong prefix, wrong length,
     *                             invalid hex characters, or an invalid EIP-55 checksum
     */
    public static void validate(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new BadRequestException("Wallet address is required and cannot be empty");
        }

        String trimmed = address.trim();

        if (!trimmed.startsWith("0x") && !trimmed.startsWith("0X")) {
            throw new BadRequestException("Wallet address must start with '0x' prefix");
        }

        if (trimmed.length() != 42) {
            throw new BadRequestException("Wallet address must be exactly 42 characters in length");
        }

        if (!EVM_HEX_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException("Wallet address contains invalid hexadecimal characters");
        }

        if (!WalletUtils.isValidAddress(trimmed)) {
            throw new BadRequestException("Wallet address is not a valid EVM address");
        }

        // If mixed-case is provided, validate EIP-55 checksum
        String hexPart = trimmed.substring(2);
        boolean hasUpper = !hexPart.equals(hexPart.toLowerCase());
        boolean hasLower = !hexPart.equals(hexPart.toUpperCase());
        if (hasUpper && hasLower) {
            String checksummed = Keys.toChecksumAddress(trimmed);
            if (!trimmed.equals(checksummed)) {
                throw new BadRequestException("Wallet address has an invalid EIP-55 checksum");
            }
        }
    }

    /**
     * Validates and normalizes an EVM address to its canonical EIP-55 checksum representation.
     *
     * @param address raw address string
     * @return normalized EIP-55 checksum address
     */
    public static String normalize(String address) {
        validate(address);
        return Keys.toChecksumAddress(address.trim());
    }
}
