package com.authentix.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.NumericType;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthentixRegistryService {

    private final Web3j web3j;

    @Value("${blockchain.contract-address}")
    private String contractAddress;

    public AuthentixRegistryService(Web3j web3j) {
        this.web3j = web3j;
    }

    public List<Type> getAsset(String assetCode) throws Exception {

        Function function = new Function(
                "getAsset",
                List.of(
                        new Utf8String(assetCode)
                ),
                Arrays.asList(
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Address>() {},
                        new TypeReference<Address>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Bool>() {}
                )
        );

        String encodedFunction =
                org.web3j.abi.FunctionEncoder.encode(function);

        EthCall response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        null,
                        contractAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send();

        return FunctionReturnDecoder.decode(
                response.getValue(),
                function.getOutputParameters()
        );
    }
}