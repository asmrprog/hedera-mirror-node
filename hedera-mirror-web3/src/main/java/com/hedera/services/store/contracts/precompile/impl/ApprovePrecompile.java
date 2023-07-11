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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BOOL;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_APPROVE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE_NFT_APPROVE;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.ApproveParams;
import com.hedera.services.store.contracts.precompile.codec.ApproveResult;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class ApprovePrecompile extends AbstractWritePrecompile {
    private static final Function ERC_TOKEN_APPROVE_FUNCTION = new Function("approve(address,uint256)", BOOL);
    private static final Bytes ERC_TOKEN_APPROVE_SELECTOR = Bytes.wrap(ERC_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TOKEN_APPROVE_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TOKEN_APPROVE_FUNCTION =
            new Function("approve(address,address,uint256)", INT_BOOL_PAIR);
    private static final Bytes HAPI_TOKEN_APPROVE_SELECTOR = Bytes.wrap(HAPI_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TOKEN_APPROVE_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_APPROVE_NFT_FUNCTION = new Function("approveNFT(address,address,uint256)", INT);
    private static final Bytes HAPI_APPROVE_NFT_SELECTOR = Bytes.wrap(HAPI_APPROVE_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_APPROVE_NFT_DECODER = TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);

    private final EncodingFacade encoder;
    private final ApproveAllowanceLogic approveAllowanceLogic;
    private final DeleteAllowanceLogic deleteAllowanceLogic;
    private final ApproveAllowanceChecks approveAllowanceChecks;
    private final DeleteAllowanceChecks deleteAllowanceChecks;

    public ApprovePrecompile(
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceLogic approveAllowanceLogic,
            final DeleteAllowanceLogic deleteAllowanceLogic,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final DeleteAllowanceChecks deleteAllowanceChecks) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.approveAllowanceLogic = approveAllowanceLogic;
        this.deleteAllowanceLogic = deleteAllowanceLogic;
        this.approveAllowanceChecks = approveAllowanceChecks;
        this.deleteAllowanceChecks = deleteAllowanceChecks;
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        TokenID tokenId = null;
        Id ownerId = null;
        boolean isFungible = true;
        Address senderAddress = null;
        Builder transactionBody;

        if (bodyParams instanceof ApproveParams approveParams) {
            tokenId = approveParams.token();
            senderAddress = approveParams.senderAddress();
            isFungible = approveParams.isFungible();
            ownerId = approveParams.ownerId();
        }

        final var nestedInput = tokenId == null ? input : input.slice(24);
        final var operatorId = Id.fromGrpcAccount(accountIdFromEvmAddress(senderAddress));
        final var approveOp = decodeTokenApprove(nestedInput, tokenId, isFungible, aliasResolver);

        if (approveOp.isFungible()) {
            transactionBody = syntheticTxnFactory.createFungibleApproval(approveOp, operatorId);
        } else {
            // Per the ERC-721 spec, "The zero address indicates there is no approved address"; so
            // translate this approveAllowance into a deleteAllowance
            if (isNftApprovalRevocation(approveOp)) {
                final var nominalOwnerId = ownerId != null ? ownerId : Id.DEFAULT;
                transactionBody = syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
            } else {
                transactionBody = syntheticTxnFactory.createNonfungibleApproval(approveOp, ownerId, operatorId);
            }
        }

        return transactionBody;
    }

    @Override
    public RunResult run(final MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();

        // We need to get all these fields from the transactionBody somehow :D
        boolean isFungible = false;
        AccountID ownerId;
        AccountID operatorId;
        // The spender id is not set in createDeleteAllowance, this might be a problem
        AccountID spenderAddress = null;
        TokenID tokenId;
        long amount = 0;
        long serialNumber = 0;
        final var senderAddress = frame.getSenderAddress();

        final var deleteAllowanceBody = transactionBody.getCryptoDeleteAllowance();
        final var isNftApprovalRevocation = deleteAllowanceBody.isInitialized();

        if (!isNftApprovalRevocation) {
            final var approveAllowanceBody = transactionBody.getCryptoApproveAllowance();
            isFungible = approveAllowanceBody.getTokenAllowances(0).isInitialized();
            final var tokenAllowances = approveAllowanceBody.getTokenAllowances(0);
            final var nftAllowances = approveAllowanceBody.getNftAllowances(0);
            ownerId = tokenAllowances.isInitialized() ? tokenAllowances.getOwner() : nftAllowances.getOwner();
            operatorId =
                    tokenAllowances.isInitialized() ? tokenAllowances.getOwner() : nftAllowances.getDelegatingSpender();
            if (operatorId == null) operatorId = nftAllowances.getOwner();
            spenderAddress =
                    tokenAllowances.isInitialized() ? tokenAllowances.getSpender() : nftAllowances.getSpender();
            tokenId = tokenAllowances.isInitialized() ? tokenAllowances.getTokenId() : nftAllowances.getTokenId();
            amount = tokenAllowances.isInitialized() ? tokenAllowances.getAmount() : 0;
            serialNumber = tokenAllowances.isInitialized() ? 0 : nftAllowances.getSerialNumbers(0);
        } else {
            ownerId = deleteAllowanceBody.getNftAllowances(0).getOwner();
            operatorId = deleteAllowanceBody.getNftAllowances(0).getOwner();
            tokenId = deleteAllowanceBody.getNftAllowances(0).getTokenId();
            serialNumber = deleteAllowanceBody.getNftAllowances(0).getSerialNumbers(0);
        }

        validateTrueOrRevert(isFungible || ownerId != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var grpcOperatorId = Objects.requireNonNull(operatorId);
        //  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
        //  an authorized operator of the current owner"
        if (isFungible) {
            final var isApproved = operatorId.equals(ownerId)
                    || store.hasApprovedForAll(asTypedEvmAddress(ownerId), grpcOperatorId, tokenId);
            validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        }

        final var payerAccount = store.getAccount(asTypedEvmAddress(grpcOperatorId), OnMissing.THROW);
        if (isNftApprovalRevocation) {
            final var revocationOp = transactionBody.getCryptoDeleteAllowance();
            final var revocationWrapper = revocationOp.getNftAllowancesList();
            final var status = deleteAllowanceChecks.deleteAllowancesValidation(revocationWrapper, payerAccount, store);
            validateTrueOrRevert(status == OK, status);
            deleteAllowanceLogic.deleteAllowance(store, new ArrayList<>(), revocationWrapper, grpcOperatorId);
        } else {
            final var status = approveAllowanceChecks.allowancesValidation(
                    transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                    transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                    transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                    payerAccount,
                    store);
            validateTrueOrRevert(status == OK, status);
            try {
                approveAllowanceLogic.approveAllowance(
                        store,
                        new TreeMap<>(),
                        new TreeMap<>(),
                        transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                        transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                        grpcOperatorId);
            } catch (final InvalidTransactionException e) {
                throw new InvalidTransactionException(e.getResponseCode(), true);
            }
        }

        final var tokenAddress = asTypedEvmAddress(tokenId);
        if (isFungible) {
            frame.addLog(getLogForFungibleAdjustAllowance(
                    tokenAddress, senderAddress, asTypedEvmAddress(spenderAddress), amount));
        } else {
            frame.addLog(getLogForNftAdjustAllowance(tokenAddress, senderAddress, null, serialNumber));
        }
        return new ApproveResult(tokenId, isFungible);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime, final TransactionBody transactionBody) {
        final var deleteAllowanceBody = transactionBody.getCryptoDeleteAllowance();
        final var isNftApprovalRevocation = deleteAllowanceBody.isInitialized();
        if (isNftApprovalRevocation) {
            return pricingUtils.getMinimumPriceInTinybars(DELETE_NFT_APPROVE, consensusTime);
        } else {
            return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
        }
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var approveResult = (ApproveResult) runResult;
        if (approveResult.tokenId() != null) {
            return encoder.encodeApprove(true);
        } else if (approveResult.isFunguble()) {
            return encoder.encodeApprove(SUCCESS.getNumber(), true);
        } else {
            return encoder.encodeApproveNFT(SUCCESS.getNumber());
        }
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_APPROVE);
    }

    public static ApproveWrapper decodeTokenApprove(
            final Bytes input,
            final TokenID impliedTokenId,
            final boolean isFungible,
            final UnaryOperator<byte[]> aliasResolver) {

        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID tokenId;

        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TOKEN_APPROVE_SELECTOR, ERC_TOKEN_APPROVE_DECODER);
            tokenId = impliedTokenId;
        } else if (isFungible) {
            decodedArguments = decodeFunctionCall(input, HAPI_TOKEN_APPROVE_SELECTOR, HAPI_TOKEN_APPROVE_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        } else {
            decodedArguments = decodeFunctionCall(input, HAPI_APPROVE_NFT_SELECTOR, HAPI_APPROVE_NFT_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        }
        final var spender = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);

        if (isFungible) {
            final var amount = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, amount, BigInteger.ZERO, true);
        } else {
            final var serialNumber = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, BigInteger.ZERO, serialNumber, false);
        }
    }

    private boolean isNftApprovalRevocation(final ApproveWrapper approveOp) {
        return Objects.requireNonNull(approveOp, "`body` method should be called before `isNftApprovalRevocation`")
                        .spender()
                        .getAccountNum()
                == 0;
    }

    private Log getLogForFungibleAdjustAllowance(
            final Address logger, final Address senderAddress, final Address spenderAddress, final long amount) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                // TODO
                // .forIndexedArgument(ledgers.canonicalAddress(senderAddress))
                // .forIndexedArgument(ledgers.canonicalAddress(spenderAddress))
                .forDataItem(amount)
                .build();
    }

    private Log getLogForNftAdjustAllowance(
            final Address logger, final Address senderAddress, final Address spenderAddress, final long serialNumber) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                // TODO
                // .forIndexedArgument(ledgers.canonicalAddress(senderAddress))
                // .forIndexedArgument(ledgers.canonicalAddress(spenderAddress))
                .forIndexedArgument(serialNumber)
                .build();
    }
}
