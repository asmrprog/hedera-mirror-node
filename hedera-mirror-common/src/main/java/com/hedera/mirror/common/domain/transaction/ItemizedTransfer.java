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
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@NoArgsConstructor
public class ItemizedTransfer {

    private Long amount;

    @Id
    private Long consensusTimestamp;

    private EntityId entityId;

    private Boolean isApproval;

    private EntityId payerAccountId;

    @JsonIgnore
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
