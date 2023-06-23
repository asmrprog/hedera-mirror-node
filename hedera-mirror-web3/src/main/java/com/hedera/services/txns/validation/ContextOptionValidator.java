/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.validation;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Copied validator type from hedera-services.
 * <p>
 * Relies on a State to determine whether various options are permissible.
 * <p>
 * Differences with the original:
 * <ol>
 * <li>Deleted unnecessary fields</li>
 * <li>Use abstraction for the state by introducing {@link Store} interface</li>
 * <li>Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}</li>
 * <li>Calculates `isDetached` by using System.currentTimeMillis</li>
 * </ol>
 */
public class ContextOptionValidator {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ContextOptionValidator(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public ResponseCodeEnum expiryStatusGiven(final Store store, final AccountID id) {
        var account = store.getAccount(asTypedEvmAddress(id), OnMissing.THROW);
        if (!mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()) {
            return OK;
        }
        final var balance = account.getBalance();
        if (balance > 0) {
            return OK;
        }
        final var isDetached = (account.getExpiry() < System.currentTimeMillis() / 1000);
        if (!isDetached) {
            return OK;
        }
        final var isContract = account.isSmartContract();
        return expiryStatusForNominallyDetached(isContract);
    }

    public ResponseCodeEnum expiryStatusGiven(final long balance, final boolean isDetached, final boolean isContract) {
        if (balance > 0 || !isDetached) {
            return OK;
        }
        return expiryStatusForNominallyDetached(isContract);
    }

    private ResponseCodeEnum expiryStatusForNominallyDetached(final boolean isContract) {
        if (isExpiryDisabled(isContract)) {
            return OK;
        }
        return isContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
    }

    private boolean isExpiryDisabled(final boolean isContract) {
        return (isContract && !mirrorNodeEvmProperties.isExpireContracts())
                || (!isContract && !mirrorNodeEvmProperties.isExpireAccounts());
    }
}
