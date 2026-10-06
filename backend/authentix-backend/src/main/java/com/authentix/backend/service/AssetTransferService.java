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

        // 1. Acquire pessimistic write lock on the Asset to prevent concurrent transfer creation races
        Asset asset = assetRepository.findByIdWithLock(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + assetId));

        // 2. Validate user existence
        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + fromUserId));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + toUserId));

        // 3. Validate user active status
        if (!Boolean.TRUE.equals(fromUser.getActive())) {
            throw new BadRequestException("Sender account is inactive");
        }

        if (!Boolean.TRUE.equals(toUser.getActive())) {
            throw new BadRequestException("Receiver account is inactive");
        }

        // 4. Validate sender != receiver
        if (fromUser.getId().equals(toUser.getId())) {
            throw new BadRequestException("Sender and receiver cannot be the same user");
        }

        // 5. Revalidate asset current custodian
        if (asset.getCurrentCustodian() == null ||
                !asset.getCurrentCustodian().getId().equals(fromUser.getId())) {
            throw new BadRequestException("Sender is not the current custodian of this asset");
        }

        // 6. Validate asset status eligibility
        if ("DEACTIVATED".equalsIgnoreCase(asset.getStatus())) {
            throw new ConflictException("Asset is deactivated and cannot be transferred");
        }

        if ("REPORTED".equalsIgnoreCase(asset.getStatus())) {
            throw new ConflictException("Asset is reported and cannot be transferred");
        }

        // 7. Prevent multiple active/pending transfers simultaneously
        if (transferRepository.existsByAssetIdAndStatus(asset.getId(), "PENDING")) {
            throw new ConflictException("An active transfer is already pending for this asset");
        }

        // 8. Validate unique transfer code
        if (transferRepository.findByTransferCode(transferCode).isPresent()) {
            throw new ConflictException("Transfer code already exists");
        }

        // 9. Persist new pending transfer
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

        // 1. Acquire pessimistic write lock on the transfer
        AssetTransfer transfer = transferRepository.findByIdWithLock(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with id: " + transferId));

        // 2. Acquire pessimistic write lock on the associated asset to prevent custody race conditions
        Asset asset = assetRepository.findByIdWithLock(transfer.getAsset().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + transfer.getAsset().getId()));

        // 3. Validate user existence & active status
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("User account is inactive");
        }

        // 4. Validate transfer state
        if ("COMPLETED".equals(transfer.getStatus())) {
            throw new ConflictException("Transfer is already completed");
        }

        if ("CANCELLED".equals(transfer.getStatus())) {
            throw new ConflictException("Transfer has been cancelled");
        }

        if (!"PENDING".equals(transfer.getStatus())) {
            throw new ConflictException("Transfer is not in a confirmable state: " + transfer.getStatus());
        }

        // 5. Custodian revalidation: Asset's current custodian MUST still be the transfer sender
        if (asset.getCurrentCustodian() == null ||
                !asset.getCurrentCustodian().getId().equals(transfer.getFromUser().getId())) {
            throw new ConflictException("Asset custody has changed; transfer sender is no longer the current custodian");
        }

        // 6. Validate asset status eligibility
        if ("DEACTIVATED".equalsIgnoreCase(asset.getStatus())) {
            throw new ConflictException("Asset is deactivated and cannot be transferred");
        }

        if ("REPORTED".equalsIgnoreCase(asset.getStatus())) {
            throw new ConflictException("Asset is reported and cannot be transferred");
        }

        // 7. Two-party confirmation and duplicate confirmation check (idempotency enforcement)
        if (transfer.getFromUser().getId().equals(user.getId())) {
            if (Boolean.TRUE.equals(transfer.getFromUserConfirmed())) {
                throw new ConflictException("Sender has already confirmed this transfer");
            }
            transfer.setFromUserConfirmed(true);
        } else if (transfer.getToUser().getId().equals(user.getId())) {
            if (Boolean.TRUE.equals(transfer.getToUserConfirmed())) {
                throw new ConflictException("Receiver has already confirmed this transfer");
            }
            transfer.setToUserConfirmed(true);
        } else {
            throw new BadRequestException("User is not part of this transfer");
        }

        // 8. Atomic completion when both parties have confirmed
        if (Boolean.TRUE.equals(transfer.getFromUserConfirmed())
                && Boolean.TRUE.equals(transfer.getToUserConfirmed())) {
            completeTransfer(transfer, asset);
        }

        return transferRepository.save(transfer);
    }

    private void completeTransfer(AssetTransfer transfer, Asset asset) {
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