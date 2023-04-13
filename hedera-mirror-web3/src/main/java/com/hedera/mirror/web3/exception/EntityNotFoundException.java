package com.hedera.mirror.web3.exception;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.mirror.web3.evm.exception.EvmException;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class EntityNotFoundException extends EvmException {

    @Serial
    private static final long serialVersionUID = -3067964948484169965L;

    public EntityNotFoundException(String message) {
        super(message);
    }
}
