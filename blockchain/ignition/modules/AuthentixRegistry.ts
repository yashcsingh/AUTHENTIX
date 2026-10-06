import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

export default buildModule("AuthentixRegistryModule", (m) => {
    const registry = m.contract("AuthentixRegistry");

    return {
        registry,
    };
});