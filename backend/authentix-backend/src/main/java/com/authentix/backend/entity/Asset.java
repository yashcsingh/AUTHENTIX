package com.authentix.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String assetCode;

    @Column(nullable = false, length = 30)
    private String assetType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, unique = true, length = 150)
    private String qrCodeValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_custodian_id")
    private User currentCustodian;

    @Column(length = 500)
    private String metadataHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = "CREATED";
        }
    }

    public Asset() {
    }

    public Long getId() {
        return id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQrCodeValue() {
        return qrCodeValue;
    }

    public void setQrCodeValue(String qrCodeValue) {
        this.qrCodeValue = qrCodeValue;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public User getCurrentCustodian() {
        return currentCustodian;
    }

    public void setCurrentCustodian(User currentCustodian) {
        this.currentCustodian = currentCustodian;
    }

    public String getMetadataHash() {
        return metadataHash;
    }

    public void setMetadataHash(String metadataHash) {
        this.metadataHash = metadataHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}