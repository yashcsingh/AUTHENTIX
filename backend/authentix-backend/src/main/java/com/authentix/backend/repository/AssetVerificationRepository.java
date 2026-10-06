package com.authentix.backend.repository;

import com.authentix.backend.entity.AssetVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetVerificationRepository
        extends JpaRepository<AssetVerification, Long> {

    List<AssetVerification> findByAssetIdOrderByVerifiedAtDesc(Long assetId);

    List<AssetVerification> findByScannerIdOrderByVerifiedAtDesc(Long scannerId);

    List<AssetVerification> findByResultOrderByVerifiedAtDesc(String result);
}