import { network } from "hardhat";

const { viem } = await network.connect();

const registry = await viem.getContractAt(
    "AuthentixRegistry",
    "0x5FbDB2315678afecb367f032d93F642f64180aa3"
);

const publicClient = await viem.getPublicClient();

const [account0, account1] = await viem.getWalletClients();

console.log("Account 0:", account0.account.address);
console.log("Account 1:", account1.account.address);

console.log("\n--- Registering Asset ---");

const registerHash = await registry.write.registerAsset([
    "PSH-KSH-2026-000001",
    "PHYSICAL_PRODUCT",
    account0.account.address,
    "demo-metadata-hash-001"
]);

await publicClient.waitForTransactionReceipt({
    hash: registerHash
});

console.log("Asset registered.");
console.log("Transaction:", registerHash);

console.log("\n--- Reading Asset ---");

const asset = await registry.read.getAsset([
    "PSH-KSH-2026-000001"
]);

console.log(asset);

console.log("\n--- Transferring Custody ---");

const transferHash = await registry.write.transferCustody(
    [
        "PSH-KSH-2026-000001",
        account1.account.address
    ],
    {
        account: account0.account
    }
);

await publicClient.waitForTransactionReceipt({
    hash: transferHash
});

console.log("Custody transferred.");
console.log("Transaction:", transferHash);

console.log("\n--- Reading Updated Asset ---");

const updatedAsset = await registry.read.getAsset([
    "PSH-KSH-2026-000001"
]);

console.log(updatedAsset);

console.log("\n--- Transfer History ---");

const history = await registry.read.getTransferHistory([
    "PSH-KSH-2026-000001"
]);

console.log(history);