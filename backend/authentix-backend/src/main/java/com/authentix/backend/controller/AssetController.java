package com.authentix.backend.controller;

import com.authentix.backend.dto.AssetResponse;
import com.authentix.backend.dto.CreateAssetRequest;
import com.authentix.backend.entity.Asset;
import com.authentix.backend.entity.User;
import com.authentix.backend.service.AssetService;
import com.authentix.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final CurrentUserService currentUserService;

    public AssetController(
            AssetService assetService,
            CurrentUserService currentUserService) {
        this.assetService = assetService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<AssetResponse> createAsset(
            @RequestBody @Valid CreateAssetRequest request) {

        User creator = currentUserService.getRequiredCurrentUser();
        Asset created = assetService.createAsset(request, creator);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AssetResponse.fromEntity(created)
        );
    }

    @GetMapping
    public ResponseEntity<List<AssetResponse>> getAllAssets() {
        return ResponseEntity.ok(
                assetService.getAllAssets().stream()
                        .map(AssetResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAssetById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                AssetResponse.fromEntity(assetService.getAssetById(id))
        );
    }

    @GetMapping("/code/{assetCode}")
    public ResponseEntity<AssetResponse> getAssetByCode(
            @PathVariable String assetCode) {

        return ResponseEntity.ok(
                AssetResponse.fromEntity(assetService.getAssetByCode(assetCode))
        );
    }
}