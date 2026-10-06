package com.authentix.backend.dto;

import com.authentix.backend.entity.AssetTransfer;

import java.time.LocalDateTime;

public class TransferResponse {

    private Long id;
    private String transferCode;
    private Long assetId;
    private String assetCode;
    private UserSummaryResponse fromUser;
    private UserSummaryResponse toUser;
    private String status;
    private Boolean fromUserConfirmed;
    private Boolean toUserConfirmed;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String blockchainTransactionId;

    public TransferResponse() {
    }

    public TransferResponse(
            Long id,
            String transferCode,
            Long assetId,
            String assetCode,
            UserSummaryResponse fromUser,
            UserSummaryResponse toUser,
            String status,
            Boolean fromUserConfirmed,
            Boolean toUserConfirmed,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            String blockchainTransactionId) {
        this.id = id;
        this.transferCode = transferCode;
        this.assetId = assetId;
        this.assetCode = assetCode;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.status = status;
        this.fromUserConfirmed = fromUserConfirmed;
        this.toUserConfirmed = toUserConfirmed;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
        this.blockchainTransactionId = blockchainTransactionId;
    }

    public static TransferResponse fromEntity(AssetTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        Long assetId = transfer.getAsset() != null ? transfer.getAsset().getId() : null;
        String assetCode = transfer.getAsset() != null ? transfer.getAsset().getAssetCode() : null;

        return new TransferResponse(
                transfer.getId(),
                transfer.getTransferCode(),
                assetId,
                assetCode,
                UserSummaryResponse.fromEntity(transfer.getFromUser()),
                UserSummaryResponse.fromEntity(transfer.getToUser()),
                transfer.getStatus(),
                transfer.getFromUserConfirmed(),
                transfer.getToUserConfirmed(),
                transfer.getRequestedAt(),
                transfer.getCompletedAt(),
                transfer.getBlockchainTransactionId()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public UserSummaryResponse getFromUser() {
        return fromUser;
    }

    public void setFromUser(UserSummaryResponse fromUser) {
        this.fromUser = fromUser;
    }

    public UserSummaryResponse getToUser() {
        return toUser;
    }

    public void setToUser(UserSummaryResponse toUser) {
        this.toUser = toUser;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getFromUserConfirmed() {
        return fromUserConfirmed;
    }

    public void setFromUserConfirmed(Boolean fromUserConfirmed) {
        this.fromUserConfirmed = fromUserConfirmed;
    }

    public Boolean getToUserConfirmed() {
        return toUserConfirmed;
    }

    public void setToUserConfirmed(Boolean toUserConfirmed) {
        this.toUserConfirmed = toUserConfirmed;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getBlockchainTransactionId() {
        return blockchainTransactionId;
    }

    public void setBlockchainTransactionId(String blockchainTransactionId) {
        this.blockchainTransactionId = blockchainTransactionId;
    }
}
