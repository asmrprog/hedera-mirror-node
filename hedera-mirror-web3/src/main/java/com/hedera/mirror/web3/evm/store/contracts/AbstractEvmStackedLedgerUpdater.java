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

package com.hedera.mirror.web3.evm.store.contracts;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;

public class AbstractEvmStackedLedgerUpdater<W extends WorldView, A extends Account>
        extends AbstractLedgerEvmWorldUpdater<AbstractLedgerEvmWorldUpdater<W, A>, UpdateTrackingAccount<A>> {

    protected MirrorEvmContractAliases mirrorEvmContractAliases;

    protected AbstractEvmStackedLedgerUpdater(
            final AbstractLedgerEvmWorldUpdater<W, A> world,
            final AccountAccessor accountAccessor,
            final TokenAccessor tokenAccessor,
            final HederaEvmEntityAccess entityAccess,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        super(world, accountAccessor, tokenAccessor, entityAccess);

        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    @Override
    public UpdateTrackingAccount<A> getForMutation(Address address) {
        final var wrapped = wrappedWorldView();
        final A account = wrapped.getForMutation(address);
        return account == null ? null : new UpdateTrackingAccount<>(account, null);
    }

    public HederaEvmContractAliases aliases() {
        return mirrorEvmContractAliases;
    }
}