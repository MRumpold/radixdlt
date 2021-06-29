/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.store;

import com.google.inject.Inject;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.ExittingOwnershipBucket;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TransactionParser {
	private final Addressing addressing;

	@Inject
	public TransactionParser(Addressing addressing) {
		this.addressing = addressing;
	}

	private String bucketToString(Bucket bucket) {
		if (bucket.getValidatorKey() != null && !(bucket instanceof ExittingOwnershipBucket)) {
			return addressing.forValidators().of(bucket.getValidatorKey());
		}

		return addressing.forAccounts().of(bucket.getOwner());
	}

	private ActionEntry mapToActionEntry(
		Optional<TwoActorEntry> maybeEntry,
		Function<REAddr, String> addrToRri,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		if (maybeEntry.isEmpty()) {
			return ActionEntry.unknown();
		}

		var entry = maybeEntry.get();
		var amtByteArray = entry.amount().toByteArray();
		var amt = UInt256.from(amtByteArray);
		var from = entry.from();
		var to = entry.to();
		final ActionType type;
		if (from.isEmpty()) {
			type = ActionType.MINT;
		} else if (to.isEmpty()) {
			type = ActionType.BURN;
		} else {
			var fromBucket = from.get();
			var toBucket = to.get();
			if (fromBucket instanceof AccountBucket) {
				if (toBucket instanceof AccountBucket) {
					type = ActionType.TRANSFER;
				} else {
					type = ActionType.STAKE;
				}
			} else if (fromBucket instanceof StakeOwnershipBucket) {
				type = ActionType.UNSTAKE;
				amt = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amt)).getLow();
			} else {
				type = ActionType.UNKNOWN;
			}
		}

		return ActionEntry.create(
			type,
			from.map(this::bucketToString).orElse(null),
			to.map(this::bucketToString).orElse(null),
			amt,
			addrToRri.apply(entry.resourceAddr().orElse(REAddr.ofNativeToken()))
		);
	}

	public Result<TxHistoryEntry> parse(
		REProcessedTxn processedTxn,
		List<Optional<TwoActorEntry>> actionEntries,
		Instant txDate,
		Function<REAddr, String> addrToRri,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		var txnId = processedTxn.getTxnId();
		var fee = processedTxn.getFeePaid();
		var message = processedTxn.getMsg()
			.map(bytes -> new String(bytes, RadixConstants.STANDARD_CHARSET));

		var actions = actionEntries.stream()
			.filter(e -> e.map(a -> !a.isFee()).orElse(true))
			.map(a -> mapToActionEntry(a, addrToRri, computeStakeFromOwnership))
			.collect(Collectors.toList());

		return Result.ok(TxHistoryEntry.create(txnId, txDate, fee, message.orElse(null), actions));
	}
}
