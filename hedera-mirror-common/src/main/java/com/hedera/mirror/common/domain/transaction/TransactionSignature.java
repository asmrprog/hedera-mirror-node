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

package com.hedera.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@IdClass(TransactionSignature.Id.class)
@NoArgsConstructor
public class TransactionSignature implements Persistable<TransactionSignature.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    private EntityId entityId;

    @jakarta.persistence.Id
    @ToString.Exclude
    private byte[] publicKeyPrefix;

    @ToString.Exclude
    private byte[] signature;

    private int type;

    @Override
    @JsonIgnore
    public TransactionSignature.Id getId() {
        TransactionSignature.Id transactionSignatureId = new TransactionSignature.Id();
        transactionSignatureId.setConsensusTimestamp(consensusTimestamp);
        transactionSignatureId.setPublicKeyPrefix(publicKeyPrefix);
        return transactionSignatureId;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true;
    }

    @Data
    public static class Id implements Serializable {
        private static final long serialVersionUID = -8758644338990079234L;
        private long consensusTimestamp;
        private byte[] publicKeyPrefix;
    }
}
