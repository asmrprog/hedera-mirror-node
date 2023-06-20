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

package com.hedera.mirror.web3.evm.store;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.*;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class StoreImpl implements Store {

    private final StackedStateFrames<Object> stackedStateFrames;

    public StoreImpl(final List<DatabaseAccessor<Object, ?>> databaseAccessors) {
        this.stackedStateFrames = new StackedStateFrames<>(databaseAccessors);
    }

    @Override
    public Account getAccount(final Address address, final boolean throwIfMissing) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        final var account = accountAccessor.get(address);

        if (throwIfMissing) {
            return account.orElseThrow(() -> missingEntityException(Account.class.getName(), address));
        } else {
            return account.orElse(new Account(Id.DEFAULT, 0L));
        }
    }

    @Override
    public Token getToken(final Address address, final boolean throwIfMissing) {
        final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        final var token = tokenAccessor.get(address);

        if (throwIfMissing) {
            return token.orElseThrow(() -> missingEntityException(Token.class.getName(), address));
        } else {
            return token.orElse(new Token(Id.DEFAULT));
        }
    }

    @Override
    public TokenRelationship getTokenRelationship(
            final TokenRelationshipKey tokenRelationshipKey, final boolean throwIfMissing) {
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
        final var tokenRelationship = tokenRelationshipAccessor.get(tokenRelationshipKey);

        if (throwIfMissing) {
            return tokenRelationship.orElseThrow(
                    () -> missingEntityException(TokenRelationship.class.getName(), tokenRelationshipKey));
        } else {
            return tokenRelationship.orElse(new TokenRelationship(new Token(Id.DEFAULT), new Account(Id.DEFAULT, 0L)));
        }
    }

    @Override
    public UniqueToken getUniqueToken(final NftId nftId, final boolean throwIfMissing) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        final var uniqueToken = uniqueTokenAccessor.get(nftId);

        if (throwIfMissing) {
            return uniqueToken.orElseThrow(() -> missingEntityException(UniqueToken.class.getName(), nftId));
        } else {
            return uniqueToken.orElse(
                    new UniqueToken(Id.DEFAULT, 0L, RichInstant.MISSING_INSTANT, Id.DEFAULT, Id.DEFAULT, new byte[0]));
        }
    }

    @Override
    public void updateAccount(final Account updatedAccount) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        accountAccessor.set(updatedAccount.getAccountAddress(), updatedAccount);
    }

    @Override
    public void updateToken(final Token updatedToken) {
        final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        tokenAccessor.set(asTypedEvmAddress(updatedToken.getId().asGrpcContract()), updatedToken);
    }

    @Override
    public void updateTokenRelationship(final TokenRelationship updatedTokenRelationship) {
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
        tokenRelationshipAccessor.set(keyFromRelationship(updatedTokenRelationship), updatedTokenRelationship);
    }

    @Override
    public void updateUniqueToken(final UniqueToken updatedUniqueToken) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        uniqueTokenAccessor.set(updatedUniqueToken.getNftId(), updatedUniqueToken);
    }

    @Override
    public void addPendingChanges() {
        stackedStateFrames.top().commit();
    }

    @Override
    public void commit() {
        final var topFrame = stackedStateFrames.top();
        if (stackedStateFrames.height() > 1) { // commit only to upstream RWCachingStateFrame
            topFrame.commit();
            stackedStateFrames.pop();
        }
    }

    @Override
    public void wrap() {
        stackedStateFrames.push();
    }

    private TokenRelationshipKey keyFromRelationship(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().getAccountAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }

    private InvalidTransactionException missingEntityException(final String type, Object id) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Entity of type %s with id %s is missing", type, id), "");
    }
}
