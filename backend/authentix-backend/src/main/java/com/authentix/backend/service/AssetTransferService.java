package com.authentix.backend.service;

import com.authentix.backend.entity.Asset;
import com.authentix.backend.entity.AssetTransfer;
import com.authentix.backend.entity.User;
import com.authentix.backend.exception.BadRequestException;
import com.authentix.backend.exception.ConflictException;
import com.authentix.backend.exception.ResourceNotFoundException;
import com.authentix.backend.repository.AssetRepository;
import com.authentix.backend.repository.AssetTransferRepository;
import com.authentix.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class AssetTransferService {

    private final AssetTransferRepository transferRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public AssetTransferService(
            AssetTransferRepository transferRepository,
            AssetRepository assetRepository,
            UserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    public AssetTransfer createTransfer(
            Long assetId,
            Long fromUserId,
            Long toUserId,
            String transferCode) {

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + fromUserId));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + toUserId));

        if (fromUser.getId().equals(toUser.getId())) {
            throw new BadRequestException("Sender and receiver cannot be the same user");
        }

        if (asset.getCurrentCustodian() == null ||
                !asset.getCurrentCustodian().getId().equals(fromUser.getId())) {

            throw new BadRequestException("Sender is not the current custodian of this asset");
        }

        if (transferRepository.findByTransferCode(transferCode).isPresent()) {
            throw new ConflictException("Transfer code already exists");
        }

        AssetTransfer transfer = new AssetTransfer();

        transfer.setTransferCode(transferCode);
        transfer.setAsset(asset);
        transfer.setFromUser(fromUser);
        transfer.setToUser(toUser);
        transfer.setStatus("PENDING");

        return transferRepository.save(transfer);
    }

    public AssetTransfer confirmTransfer(
            Long transferId,
            Long userId) {

        AssetTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with id: " + transferId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (transfer.getStatus().equals("COMPLETED")) {
            throw new ConflictException("Transfer is already completed");
        }

        if (transfer.getStatus().equals("CANCELLED")) {
            throw new ConflictException("Transfer has been cancelled");
        }

        if (transfer.getFromUser().getId().equals(user.getId())) {

            transfer.setFromUserConfirmed(true);

        } else if (transfer.getToUser().getId().equals(user.getId())) {

            transfer.setToUserConfirmed(true);

        } else {

            throw new BadRequestException("User is not part of this transfer");
        }

        if (Boolean.TRUE.equals(transfer.getFromUserConfirmed())
                && Boolean.TRUE.equals(transfer.getToUserConfirmed())) {

            completeTransfer(transfer);
        }

        return transferRepository.save(transfer);
    }

    private void completeTransfer(AssetTransfer transfer) {

        Asset asset = transfer.getAsset();

        asset.setCurrentCustodian(transfer.getToUser());
        asset.setStatus("TRANSFERRED");

        assetRepository.save(asset);

        transfer.setStatus("COMPLETED");
        transfer.setCompletedAt(LocalDateTime.now());
    }

    public List<AssetTransfer> getTransfersForAsset(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset not found with id: " + assetId);
        }
        return transferRepository.findByAssetIdOrderByRequestedAtDesc(assetId);
    }

    public AssetTransfer getTransfer(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with id: " + transferId));
    }
}