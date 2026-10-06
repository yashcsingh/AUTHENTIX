package com.authentix.backend.dto;

import com.authentix.backend.entity.Asset;

import java.time.LocalDateTime;

public class AssetResponse {

    private Long id;
    private String assetCode;
    private String assetType;
    private String status;
    private String qrCodeValue;
    private UserSummaryResponse creator;
    private UserSummaryResponse currentCustodian;
    private String metadataHash;
    private LocalDateTime createdAt;

    public AssetResponse() {
    }

    public AssetResponse(
            Long id,
            String assetCode,
            String assetType,
            String status,
            String qrCodeValue,
            UserSummaryResponse creator,
            UserSummaryResponse currentCustodian,
            String metadataHash,
            LocalDateTime createdAt) {
        this.id = id;
        this.assetCode = assetCode;
        this.assetType = assetType;
        this.status = status;
        this.qrCodeValue = qrCodeValue;
        this.creator = creator;
        this.currentCustodian = currentCustodian;
        this.metadataHash = metadataHash;
        this.createdAt = createdAt;
    }

    public static AssetResponse fromEntity(Asset asset) {
        if (asset == null) {
            return null;
        }
        return new AssetResponse(
                asset.getId(),
                asset.getAssetCode(),
                asset.getAssetType(),
                asset.getStatus(),
                asset.getQrCodeValue(),
                UserSummaryResponse.fromEntity(asset.getCreator()),
                UserSummaryResponse.fromEntity(asset.getCurrentCustodian()),
                asset.getMetadataHash(),
                asset.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public UserSummaryResponse getCreator() {
        return creator;
    }

    public void setCreator(UserSummaryResponse creator) {
        this.creator = creator;
    }

    public UserSummaryResponse getCurrentCustodian() {
        return currentCustodian;
    }

    public void setCurrentCustodian(UserSummaryResponse currentCustodian) {
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
