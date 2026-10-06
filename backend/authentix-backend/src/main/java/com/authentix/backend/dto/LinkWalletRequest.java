package com.authentix.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LinkWalletRequest {

    @NotBlank(message = "Wallet address is required")
    @Size(min = 42, max = 42, message = "Wallet address must be exactly 42 characters")
    @Pattern(regexp = "^0[xX][0-9a-fA-F]{40}$", message = "Wallet address must be a valid hexadecimal EVM address starting with 0x")
    private String walletAddress;

    public LinkWalletRequest() {
    }

    public LinkWalletRequest(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }
}
