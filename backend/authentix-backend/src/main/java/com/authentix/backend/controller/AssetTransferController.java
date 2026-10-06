package com.authentix.backend.controller;

import com.authentix.backend.dto.ConfirmTransferRequest;
import com.authentix.backend.dto.CreateTransferRequest;
import com.authentix.backend.dto.TransferResponse;
import com.authentix.backend.entity.AssetTransfer;
import com.authentix.backend.entity.User;
import com.authentix.backend.service.AssetTransferService;
import com.authentix.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transfers")
public class AssetTransferController {

    private final AssetTransferService transferService;
    private final CurrentUserService currentUserService;

    public AssetTransferController(
            AssetTransferService transferService,
            CurrentUserService currentUserService) {
        this.transferService = transferService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestBody @Valid CreateTransferRequest request) {

        User fromUser = currentUserService.getRequiredCurrentUser();

        AssetTransfer transfer = transferService.createTransfer(
                request.getAssetId(),
                fromUser.getId(),
                request.getToUserId(),
                request.getTransferCode()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                TransferResponse.fromEntity(transfer)
        );
    }

    @PostMapping("/{transferId}/confirm")
    public ResponseEntity<TransferResponse> confirmTransfer(
            @PathVariable Long transferId,
            @RequestBody(required = false) @Valid ConfirmTransferRequest request) {

        User currentUser = currentUserService.getRequiredCurrentUser();

        AssetTransfer transfer = transferService.confirmTransfer(
                transferId,
                currentUser.getId()
        );

        return ResponseEntity.ok(
                TransferResponse.fromEntity(transfer)
        );
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable Long transferId) {

        return ResponseEntity.ok(
                TransferResponse.fromEntity(transferService.getTransfer(transferId))
        );
    }

    @GetMapping("/asset/{assetId}")
    public ResponseEntity<List<TransferResponse>> getAssetTransfers(
            @PathVariable Long assetId) {

        return ResponseEntity.ok(
                transferService.getTransfersForAsset(assetId).stream()
                        .map(TransferResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}