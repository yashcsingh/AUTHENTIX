package com.authentix.backend.repository;

import com.authentix.backend.entity.AssetTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetTransferRepository extends JpaRepository<AssetTransfer, Long> {

    Optional<AssetTransfer> findByTransferCode(String transferCode);

    List<AssetTransfer> findByAssetIdOrderByRequestedAtDesc(Long assetId);

    List<AssetTransfer> findByFromUserIdOrderByRequestedAtDesc(Long userId);

    List<AssetTransfer> findByToUserIdOrderByRequestedAtDesc(Long userId);
}