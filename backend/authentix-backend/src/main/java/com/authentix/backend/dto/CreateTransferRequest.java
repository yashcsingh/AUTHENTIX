package com.authentix.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateTransferRequest {

    @NotNull(message = "Asset ID is required")
    @Positive(message = "Asset ID must be a positive number")
    private Long assetId;

    @NotNull(message = "Receiver user ID is required")
    @Positive(message = "Receiver user ID must be a positive number")
    private Long toUserId;

    @NotBlank(message = "Transfer code is required")
    @Size(max = 100, message = "Transfer code cannot exceed 100 characters")
    private String transferCode;

    public CreateTransferRequest() {
    }

    public CreateTransferRequest(Long assetId, Long toUserId, String transferCode) {
        this.assetId = assetId;
        this.toUserId = toUserId;
        this.transferCode = transferCode;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }
}
