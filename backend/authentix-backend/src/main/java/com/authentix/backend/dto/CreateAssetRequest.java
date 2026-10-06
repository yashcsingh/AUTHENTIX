package com.authentix.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAssetRequest {

    @NotBlank(message = "Asset code is required")
    @Size(max = 100, message = "Asset code cannot exceed 100 characters")
    private String assetCode;

    @NotBlank(message = "Asset type is required")
    @Size(max = 30, message = "Asset type cannot exceed 30 characters")
    private String assetType;

    @Size(max = 500, message = "Metadata hash cannot exceed 500 characters")
    private String metadataHash;

    public CreateAssetRequest() {
    }

    public CreateAssetRequest(String assetCode, String assetType, String metadataHash) {
        this.assetCode = assetCode;
        this.assetType = assetType;
        this.metadataHash = metadataHash;
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

    public String getMetadataHash() {
        return metadataHash;
    }

    public void setMetadataHash(String metadataHash) {
        this.metadataHash = metadataHash;
    }
}
