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

package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.SubType.*;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeBuilder;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 */
public class PrecompilePricingUtils {

    public static final JKey EMPTY_KEY;
    /**
     * If we lack an entry (because of a bad data load), return a value that cannot reasonably be paid. In this case $1
     * Million Dollars.
     */
    static final long COST_PROHIBITIVE = 1_000_000L * 10_000_000_000L;

    private static final Query SYNTHETIC_REDIRECT_QUERY = Query.newBuilder()
            .setTransactionGetRecord(TransactionGetRecordQuery.newBuilder().build())
            .build();

    static {
        EMPTY_KEY = asFcKeyUnchecked(
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    }

    final Map<GasCostType, Long> canonicalOperationCostsInTinyCents;
    private final HbarCentExchange exchange;
    private final FeeCalculator feeCalculator;
    private final UsagePricesProvider resourceCosts;
    private final AccessorFactory accessorFactory;

    public PrecompilePricingUtils(
            final AssetsLoader assetsLoader,
            final HbarCentExchange exchange,
            final FeeCalculator feeCalculator,
            final UsagePricesProvider resourceCosts,
            final AccessorFactory accessorFactory) {
        this.exchange = exchange;
        this.feeCalculator = feeCalculator;
        this.resourceCosts = resourceCosts;
        this.accessorFactory = accessorFactory;

        canonicalOperationCostsInTinyCents = new EnumMap<>(GasCostType.class);
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices;
        try {
            canonicalPrices = assetsLoader.loadCanonicalPrices();
        } catch (final IOException e) {
            throw new CanonicalOperationsUnloadableException(e);
        }
        for (final var costType : GasCostType.values()) {
            if (canonicalPrices.containsKey(costType.functionality)) {
                final BigDecimal costInUSD =
                        canonicalPrices.get(costType.functionality).get(costType.subtype);
                if (costInUSD != null) {
                    canonicalOperationCostsInTinyCents.put(
                            costType,
                            costInUSD
                                    .multiply(BigDecimal.valueOf(100 * 100_000_000L))
                                    .longValue());
                }
            }
        }
    }

    public long getCanonicalPriceInTinyCents(final GasCostType gasCostType) {
        return canonicalOperationCostsInTinyCents.getOrDefault(gasCostType, COST_PROHIBITIVE);
    }

    public long getMinimumPriceInTinybars(final GasCostType gasCostType, final Timestamp timestamp) {
        return FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), getCanonicalPriceInTinyCents(gasCostType));
    }

    public long gasFeeInTinybars(
            final TransactionBody.Builder txBody,
            final Timestamp timestamp,
            final Store store,
            final HederaEvmContractAliases mirrorEvmContractAliases) {
        final var signedTxn = SignedTransaction.newBuilder()
                .setBodyBytes(txBody.build().toByteString())
                .setSigMap(SignatureMap.getDefaultInstance())
                .build();
        final var txn = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTxn.toByteString())
                .build();
        final var accessor = accessorFactory.uncheckedSpecializedAccessor(txn);
        final var fees = feeCalculator.computeFee(accessor, EMPTY_KEY, store, timestamp, mirrorEvmContractAliases);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    public long computeViewFunctionGas(final Timestamp now, final long minimumTinybarCost, final Store store) {
        final var usagePrices = resourceCosts.defaultPricesGiven(TokenGetInfo, now);
        final var fees = feeCalculator.estimatePayment(SYNTHETIC_REDIRECT_QUERY, usagePrices, store, now, ANSWER_ONLY);

        final long gasPriceInTinybars = feeCalculator.estimatedGasPriceInTinybars(ContractCall, now);
        final long calculatedFeeInTinybars = fees.getNetworkFee() + fees.getNodeFee() + fees.getServiceFee();
        final long actualFeeInTinybars = Math.max(minimumTinybarCost, calculatedFeeInTinybars);

        // convert to gas cost
        final long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

        // charge premium
        return baseGasCost + (baseGasCost / 5L);
    }

    public long computeGasRequirement(
            final long blockTimestamp,
            final Precompile precompile,
            final TransactionBody.Builder transactionBody,
            final Store store,
            final HederaEvmContractAliases mirrorEvmContractAliases) {
        final Timestamp timestamp =
                Timestamp.newBuilder().setSeconds(blockTimestamp).build();
        final long gasPriceInTinybars = feeCalculator.estimatedGasPriceInTinybars(ContractCall, timestamp);

        final long calculatedFeeInTinybars = gasFeeInTinybars(
                transactionBody.setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(timestamp)
                        .build()),
                timestamp,
                store,
                mirrorEvmContractAliases);

        final long minimumFeeInTinybars = precompile.getMinimumFeeInTinybars(timestamp, transactionBody.build());
        final long actualFeeInTinybars = Math.max(minimumFeeInTinybars, calculatedFeeInTinybars);

        // convert to gas cost
        final long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

        // charge premium
        return baseGasCost + (baseGasCost / 5L);
    }

    public enum GasCostType {
        UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
        CRYPTO_CREATE(CryptoCreate, DEFAULT),
        CRYPTO_UPDATE(CryptoUpdate, DEFAULT),
        TRANSFER_HBAR(CryptoTransfer, DEFAULT),
        TRANSFER_FUNGIBLE(CryptoTransfer, TOKEN_FUNGIBLE_COMMON),
        TRANSFER_NFT(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE),
        TRANSFER_FUNGIBLE_CUSTOM_FEES(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
        TRANSFER_NFT_CUSTOM_FEES(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
        MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
        MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE),
        BURN_FUNGIBLE(TokenBurn, TOKEN_FUNGIBLE_COMMON),
        DELETE(TokenDelete, DEFAULT),
        BURN_NFT(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE),
        ASSOCIATE(TokenAssociateToAccount, DEFAULT),
        DISSOCIATE(TokenDissociateFromAccount, DEFAULT),
        APPROVE(CryptoApproveAllowance, DEFAULT),
        DELETE_NFT_APPROVE(CryptoDeleteAllowance, DEFAULT),
        GRANT_KYC(TokenGrantKycToAccount, DEFAULT),
        REVOKE_KYC(TokenRevokeKycFromAccount, DEFAULT),
        PAUSE(TokenPause, DEFAULT),
        UNPAUSE(TokenUnpause, DEFAULT),
        FREEZE(TokenFreezeAccount, DEFAULT),
        UNFREEZE(TokenUnfreezeAccount, DEFAULT),
        WIPE_FUNGIBLE(TokenAccountWipe, TOKEN_FUNGIBLE_COMMON),
        WIPE_NFT(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE),
        UPDATE(TokenUpdate, DEFAULT),
        PRNG(HederaFunctionality.UtilPrng, DEFAULT);

        final HederaFunctionality functionality;
        final SubType subtype;

        GasCostType(final HederaFunctionality functionality, final SubType subtype) {
            this.functionality = functionality;
            this.subtype = subtype;
        }
    }

    static class CanonicalOperationsUnloadableException extends RuntimeException {

        static final long serialVersionUID = 1L;

        public CanonicalOperationsUnloadableException(final Exception e) {
            super("Canonical prices for precompiles are not available", e);
        }
    }
}
