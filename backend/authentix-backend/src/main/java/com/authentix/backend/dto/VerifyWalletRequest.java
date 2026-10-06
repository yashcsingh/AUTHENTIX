package com.authentix.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyWalletRequest {

    @NotBlank(message = "Wallet address is required")
    @Size(min = 42, max = 42, message = "Wallet address must be exactly 42 characters")
    @Pattern(regexp = "^0[xX][0-9a-fA-F]{40}$", message = "Wallet address must be a valid hexadecimal EVM address starting with 0x")
    private String walletAddress;

    @NotBlank(message = "Challenge is required")
    private String challenge;

    @NotBlank(message = "Signature is required")
    private String signature;

    public VerifyWalletRequest() {
    }

    public VerifyWalletRequest(String walletAddress, String challenge, String signature) {
        this.walletAddress = walletAddress;
        this.challenge = challenge;
        this.signature = signature;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
