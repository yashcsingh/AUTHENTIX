package com.authentix.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_verifications")
public class AssetVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String verificationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanner_id", nullable = false)
    private User scanner;

    @Column(nullable = false, length = 40)
    private String result;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        verifiedAt = LocalDateTime.now();
    }

    public AssetVerification() {
    }

    public Long getId() {
        return id;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public User getScanner() {
        return scanner;
    }

    public void setScanner(User scanner) {
        this.scanner = scanner;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}