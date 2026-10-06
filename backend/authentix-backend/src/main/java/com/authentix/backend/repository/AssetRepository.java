package com.authentix.backend.repository;

import com.authentix.backend.entity.Asset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetCode(String assetCode);

    Optional<Asset> findByQrCodeValue(String qrCodeValue);

    boolean existsByAssetCode(String assetCode);

    boolean existsByQrCodeValue(String qrCodeValue);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Asset a WHERE a.id = :id")
    Optional<Asset> findByIdWithLock(@Param("id") Long id);
}