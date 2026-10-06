package com.authentix.backend.service;

import com.authentix.backend.dto.CreateAssetRequest;
import com.authentix.backend.entity.Asset;
import com.authentix.backend.entity.User;
import com.authentix.backend.exception.ConflictException;
import com.authentix.backend.exception.ResourceNotFoundException;
import com.authentix.backend.repository.AssetRepository;
import com.authentix.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public AssetService(
            AssetRepository assetRepository,
            UserRepository userRepository) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    public Asset createAsset(CreateAssetRequest request, User creator) {
        String assetCode = request.getAssetCode().trim();
        if (assetRepository.existsByAssetCode(assetCode)) {
            throw new ConflictException("Asset code already exists");
        }

        String qrCodeValue = "AUTHENTIX://PRODUCT/" + assetCode;
        if (assetRepository.existsByQrCodeValue(qrCodeValue)) {
            throw new ConflictException("QR code already registered");
        }

        Asset asset = new Asset();
        asset.setAssetCode(assetCode);
        asset.setAssetType(request.getAssetType().trim());
        asset.setMetadataHash(request.getMetadataHash() != null ? request.getMetadataHash().trim() : null);
        asset.setQrCodeValue(qrCodeValue);
        asset.setCreator(creator);
        asset.setCurrentCustodian(creator);
        asset.setStatus("CREATED");

        return assetRepository.save(asset);
    }

    public Asset createAsset(Asset asset, Long creatorId) {
        if (assetRepository.existsByAssetCode(asset.getAssetCode())) {
            throw new ConflictException("Asset code already exists");
        }

        if (asset.getQrCodeValue() == null || asset.getQrCodeValue().isBlank()) {
            asset.setQrCodeValue(
                    "AUTHENTIX://PRODUCT/" + asset.getAssetCode()
            );
        }

        if (assetRepository.existsByQrCodeValue(asset.getQrCodeValue())) {
            throw new ConflictException("QR code already registered");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found with id: " + creatorId));

        asset.setCreator(creator);

        if (asset.getCurrentCustodian() == null) {
            asset.setCurrentCustodian(creator);
        }

        if (asset.getStatus() == null || asset.getStatus().isBlank()) {
            asset.setStatus("CREATED");
        }

        return assetRepository.save(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + id));
    }

    public Asset getAssetByCode(String assetCode) {
        return assetRepository.findByAssetCode(assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with code: " + assetCode));
    }
}