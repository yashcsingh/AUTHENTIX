package com.authentix.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_transfers")
public class AssetTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String transferCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private Boolean fromUserConfirmed = false;

    @Column(nullable = false)
    private Boolean toUserConfirmed = false;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    @Column(length = 100)
    private String blockchainTransactionId;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();

        if (status == null) {
            status = "PENDING";
        }

        if (fromUserConfirmed == null) {
            fromUserConfirmed = false;
        }

        if (toUserConfirmed == null) {
            toUserConfirmed = false;
        }
    }

    public AssetTransfer() {
    }

    public Long getId() {
        return id;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public User getFromUser() {
        return fromUser;
    }

    public void setFromUser(User fromUser) {
        this.fromUser = fromUser;
    }

    public User getToUser() {
        return toUser;
    }

    public void setToUser(User toUser) {
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