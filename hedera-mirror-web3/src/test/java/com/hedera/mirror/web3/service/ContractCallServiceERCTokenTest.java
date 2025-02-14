/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractCallServiceERCTokenTest extends ContractCallTestSetup {

    @ParameterizedTest
    @EnumSource(ErcContractReadOnlyFunctions.class)
    void ercReadOnlyPrecompileOperationsTest(ErcContractReadOnlyFunctions ercFunction) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);
        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void ercModificationPrecompileOperationsTest(ErcContractModificationFunctions ercFunction) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    @Test
    void metadataOf() {
        final var functionHash = functionEncodeDecoder.functionHashFor("tokenURI", ERC_ABI_PATH, NFT_ADDRESS, 1L);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThat(contractCallService.processCall(serviceParameters)).isNotEqualTo(Address.ZERO.toString());
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "delegateTransfer", ERC_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L);
        final var serviceParameters = serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L);

        assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Precompile not supported for non-static frames");
    }

    @RequiredArgsConstructor
    public enum ErcContractReadOnlyFunctions {
        GET_APPROVED_EMPTY_SPENDER("getApproved", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Long[] {13L}),
        GET_APPROVED("getApproved", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SPENDER_ADDRESS}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"HBAR"}),
        BALANCE_OF("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Long[] {12L}),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SPENDER_ADDRESS}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;
    }

    @RequiredArgsConstructor
    public enum ErcContractModificationFunctions {
        TRANSFER("transfer", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L}),
        TRANSFER_FROM("transferFrom", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS, 2L}),
        APPROVE("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, 2L});

        private final String name;
        private final Object[] functionParameters;
    }
}
