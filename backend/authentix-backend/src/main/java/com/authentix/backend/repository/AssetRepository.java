package com.authentix.backend.repository;

import com.authentix.backend.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetCode(String assetCode);

    Optional<Asset> findByQrCodeValue(String qrCodeValue);

    boolean existsByAssetCode(String assetCode);

    boolean existsByQrCodeValue(String qrCodeValue);
}