package com.authentix.backend.dto;

import java.time.LocalDateTime;

public class WalletChallengeResponse {

    private String challenge;
    private String walletAddress;
    private LocalDateTime expiresAt;
    private String message;

    public WalletChallengeResponse() {
    }

    public WalletChallengeResponse(String challenge, String walletAddress, LocalDateTime expiresAt, String message) {
        this.challenge = challenge;
        this.walletAddress = walletAddress;
        this.expiresAt = expiresAt;
        this.message = message;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
