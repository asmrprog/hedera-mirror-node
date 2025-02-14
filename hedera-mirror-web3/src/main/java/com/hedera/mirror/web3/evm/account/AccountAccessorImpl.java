/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.account;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    public static final int EVM_ADDRESS_SIZE = 20;

    private final HederaEvmEntityAccess mirrorEntityAccess;
    private final Store store;

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        final var account = store.getAccount(addressOrAlias, OnMissing.DONT_THROW);
        if (!account.isEmptyAccount()) {
            return addressOrAlias;
        }

        return getAddressOrAlias(addressOrAlias);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return mirrorEntityAccess.isTokenAccount(address);
    }

    public Address getAddressOrAlias(final Address address) {
        final ByteString alias;
        if (!mirrorEntityAccess.isExtant(address)) {
            return address;
        }
        alias = mirrorEntityAccess.alias(address);

        if (!alias.isEmpty() && alias.size() == EVM_ADDRESS_SIZE) {
            return Address.wrap(Bytes.wrap(alias.toByteArray()));
        }
        return address;
    }
}
