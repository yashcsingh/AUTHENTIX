// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract AuthentixRegistry {

    enum AssetStatus {
        CREATED,
        ACTIVE,
        TRANSFERRED,
        REPORTED,
        DEACTIVATED
    }

    struct Asset {
        string assetCode;
        string assetType;
        address creator;
        address currentCustodian;
        string metadataHash;
        AssetStatus status;
        uint256 createdAt;
        bool exists;
    }

    struct Transfer {
        uint256 transferId;
        string assetCode;
        address from;
        address to;
        uint256 timestamp;
    }

    mapping(string => Asset) private assets;

    mapping(string => Transfer[]) private transferHistory;

    uint256 private nextTransferId = 1;

    event AssetRegistered(
        string indexed assetCode,
        address indexed creator,
        string assetType,
        uint256 timestamp
    );

    event CustodyTransferred(
        string indexed assetCode,
        address indexed from,
        address indexed to,
        uint256 transferId,
        uint256 timestamp
    );

    event AssetStatusUpdated(
        string indexed assetCode,
        AssetStatus status
    );

    function registerAsset(
        string calldata assetCode,
        string calldata assetType,
        address creator,
        string calldata metadataHash
    ) external {

        require(!assets[assetCode].exists, "Asset already registered");

        require(
            creator != address(0),
            "Invalid creator address"
        );

        assets[assetCode] = Asset({
            assetCode: assetCode,
            assetType: assetType,
            creator: creator,
            currentCustodian: creator,
            metadataHash: metadataHash,
            status: AssetStatus.CREATED,
            createdAt: block.timestamp,
            exists: true
        });

        emit AssetRegistered(
            assetCode,
            creator,
            assetType,
            block.timestamp
        );
    }

    function transferCustody(
        string calldata assetCode,
        address newCustodian
    ) external {

        require(
            assets[assetCode].exists,
            "Asset not registered"
        );

        require(
            newCustodian != address(0),
            "Invalid custodian address"
        );

        require(
            msg.sender == assets[assetCode].currentCustodian,
            "Only current custodian can transfer"
        );

        address previousCustodian =
            assets[assetCode].currentCustodian;

        assets[assetCode].currentCustodian =
            newCustodian;

        assets[assetCode].status =
            AssetStatus.TRANSFERRED;

        uint256 transferId = nextTransferId;

        nextTransferId++;

        transferHistory[assetCode].push(
            Transfer({
                transferId: transferId,
                assetCode: assetCode,
                from: previousCustodian,
                to: newCustodian,
                timestamp: block.timestamp
            })
        );

        emit CustodyTransferred(
            assetCode,
            previousCustodian,
            newCustodian,
            transferId,
            block.timestamp
        );
    }

    function updateAssetStatus(
        string calldata assetCode,
        AssetStatus newStatus
    ) external {

        require(
            assets[assetCode].exists,
            "Asset not registered"
        );

        require(
            msg.sender == assets[assetCode].creator,
            "Only creator can update status"
        );

        assets[assetCode].status = newStatus;

        emit AssetStatusUpdated(
            assetCode,
            newStatus
        );
    }

    function getAsset(
        string calldata assetCode
    )
        external
        view
        returns (
            string memory,
            string memory,
            address,
            address,
            string memory,
            AssetStatus,
            uint256,
            bool
        )
    {
        require(
            assets[assetCode].exists,
            "Asset not registered"
        );

        Asset memory asset = assets[assetCode];

        return (
            asset.assetCode,
            asset.assetType,
            asset.creator,
            asset.currentCustodian,
            asset.metadataHash,
            asset.status,
            asset.createdAt,
            asset.exists
        );
    }

    function getTransferHistory(
        string calldata assetCode
    )
        external
        view
        returns (Transfer[] memory)
    {
        require(
            assets[assetCode].exists,
            "Asset not registered"
        );

        return transferHistory[assetCode];
    }
}