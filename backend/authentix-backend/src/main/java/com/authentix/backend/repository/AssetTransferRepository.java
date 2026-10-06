package com.authentix.backend.repository;

import com.authentix.backend.entity.AssetTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetTransferRepository extends JpaRepository<AssetTransfer, Long> {

    Optional<AssetTransfer> findByTransferCode(String transferCode);

    List<AssetTransfer> findByAssetIdOrderByRequestedAtDesc(Long assetId);

    List<AssetTransfer> findByFromUserIdOrderByRequestedAtDesc(Long userId);

    List<AssetTransfer> findByToUserIdOrderByRequestedAtDesc(Long userId);

    boolean existsByAssetIdAndStatus(Long assetId, String status);

    Optional<AssetTransfer> findByAssetIdAndStatus(Long assetId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AssetTransfer t WHERE t.id = :id")
    Optional<AssetTransfer> findByIdWithLock(@Param("id") Long id);
}