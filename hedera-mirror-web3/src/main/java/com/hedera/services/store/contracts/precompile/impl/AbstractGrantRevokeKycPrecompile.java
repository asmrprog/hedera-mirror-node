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

package com.hedera.services.store.contracts.precompile.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;

public abstract class AbstractGrantRevokeKycPrecompile extends AbstractWritePrecompile {
    protected final MirrorEvmContractAliases aliases;
    protected GrantRevokeKycWrapper<TokenID, AccountID> grantRevokeOp;
    protected Id tokenId;
    protected Id accountId;
    protected HederaFunctionality function = null;
    protected SyntheticTxnFactory syntheticTxnFactory;

    protected AbstractGrantRevokeKycPrecompile(
            MirrorEvmContractAliases aliases,
            SyntheticTxnFactory syntheticTxnFactory,
            PrecompilePricingUtils pricingUtils) {
        super(pricingUtils);
        this.aliases = aliases;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    public void initialise() {
        requireNonNull(grantRevokeOp);
    }
}
