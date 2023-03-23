/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.hapi.utils.contracts;

import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BOOL;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT_BOOL_PAIR_RETURN_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.RESPONSE_STATUS_AT_BEGINNING;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.STRING;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.TOKEN_INFO;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.UINT256;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.UINT8;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.addressTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.bigIntegerTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.booleanTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.burnReturnType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.decimalsType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.getFungibleTokenInfoType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.getNonFungibleTokenInfoType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.getTokenCustomFeesType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.getTokenInfoType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.hapiGetApprovedType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.intAddressTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.intBoolTuple;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.mintReturnType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.notSpecifiedType;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.stringTuple;
import static org.junit.jupiter.api.Assertions.*;

import com.esaulpaugh.headlong.abi.TupleType;
import org.junit.jupiter.api.Test;

import com.hedera.services.hapi.utils.contracts.ParsingConstants.FunctionType;

class ParsingConstantsTest {

    @Test
    void functionTypeValidation() {
        assertEquals("ERC_ALLOWANCE", FunctionType.ERC_ALLOWANCE.name());
        assertEquals("ERC_APPROVE", FunctionType.ERC_APPROVE.name());
        assertEquals("ERC_BALANCE", FunctionType.ERC_BALANCE.name());
        assertEquals("ERC_IS_APPROVED_FOR_ALL", FunctionType.ERC_IS_APPROVED_FOR_ALL.name());
        assertEquals("ERC_DECIMALS", FunctionType.ERC_DECIMALS.name());
        assertEquals("ERC_TRANSFER", FunctionType.ERC_TRANSFER.name());
        assertEquals("ERC_GET_APPROVED", FunctionType.ERC_GET_APPROVED.name());
        assertEquals("ERC_OWNER", FunctionType.ERC_OWNER.name());
        assertEquals("ERC_SYMBOL", FunctionType.ERC_SYMBOL.name());
        assertEquals("ERC_TOTAL_SUPPLY", FunctionType.ERC_TOTAL_SUPPLY.name());
        assertEquals("ERC_TOKEN_URI", FunctionType.ERC_TOKEN_URI.name());
        assertEquals("ERC_NAME", FunctionType.ERC_NAME.name());
        assertEquals("HAPI_GET_FUNGIBLE_TOKEN_INFO", FunctionType.HAPI_GET_FUNGIBLE_TOKEN_INFO.name());
        assertEquals("HAPI_GET_NON_FUNGIBLE_TOKEN_INFO", FunctionType.HAPI_GET_NON_FUNGIBLE_TOKEN_INFO.name());
        assertEquals("HAPI_GET_TOKEN_INFO", FunctionType.HAPI_GET_TOKEN_INFO.name());
        assertEquals("HAPI_GET_TOKEN_CUSTOM_FEES", FunctionType.HAPI_GET_TOKEN_CUSTOM_FEES.name());
        assertEquals("HAPI_MINT", FunctionType.HAPI_MINT.name());
        assertEquals("HAPI_BURN", FunctionType.HAPI_BURN.name());
        assertEquals("HAPI_CREATE", FunctionType.HAPI_CREATE.name());
        assertEquals("HAPI_ALLOWANCE", FunctionType.HAPI_ALLOWANCE.name());
        assertEquals("HAPI_APPROVE_NFT", FunctionType.HAPI_APPROVE_NFT.name());
        assertEquals("HAPI_APPROVE", FunctionType.HAPI_APPROVE.name());
        assertEquals("HAPI_GET_APPROVED", FunctionType.HAPI_GET_APPROVED.name());
        assertEquals("HAPI_IS_APPROVED_FOR_ALL", FunctionType.HAPI_IS_APPROVED_FOR_ALL.name());
        assertEquals("NOT_SPECIFIED", FunctionType.NOT_SPECIFIED.name());
    }

    @Test
    void tupleTypesValidation() {
        assertEquals(bigIntegerTuple, TupleType.parse(UINT256));
        assertEquals(booleanTuple, TupleType.parse(BOOL));
        assertEquals(decimalsType, TupleType.parse(UINT8));
        assertEquals(addressTuple, TupleType.parse(ADDRESS));
        assertEquals(stringTuple, TupleType.parse(STRING));
        assertEquals(notSpecifiedType, TupleType.parse(INT32));
        assertEquals(intBoolTuple, TupleType.parse(INT_BOOL_PAIR_RETURN_TYPE));
        assertEquals(burnReturnType, TupleType.parse("(int32,uint64)"));
        assertEquals(intAddressTuple, TupleType.parse("(int32,address)"));
        assertEquals(mintReturnType, TupleType.parse("(int32,uint64,int64[])"));
        assertEquals(hapiAllowanceOfType, TupleType.parse("(int32,uint256)"));
        assertEquals(hapiGetApprovedType, TupleType.parse("(int32,bytes32)"));
        assertEquals(
                getFungibleTokenInfoType, TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO + ")"));
        assertEquals(getTokenInfoType, TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO + ")"));
        assertEquals(
                getNonFungibleTokenInfoType,
                TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO + ")"));
        assertEquals(
                getTokenCustomFeesType,
                TupleType.parse(RESPONSE_STATUS_AT_BEGINNING
                        + FIXED_FEE
                        + ARRAY_BRACKETS
                        + ","
                        + FRACTIONAL_FEE
                        + ARRAY_BRACKETS
                        + ","
                        + ROYALTY_FEE
                        + ARRAY_BRACKETS
                        + ")"));
    }
}
