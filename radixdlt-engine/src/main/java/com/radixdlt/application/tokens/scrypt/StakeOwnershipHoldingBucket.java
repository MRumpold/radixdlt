/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ImmutableAddrs;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;

import java.util.Objects;

public class StakeOwnershipHoldingBucket implements ReducerState {
	private final UInt384 shareAmount;
	private final REAddr accountAddr;
	private final ECPublicKey delegate;

	public StakeOwnershipHoldingBucket(StakeOwnership stakeOwnership) {
		this(stakeOwnership.getDelegateKey(), stakeOwnership.getOwner(), UInt384.from(stakeOwnership.getAmount()));
	}

	public StakeOwnershipHoldingBucket(
		ECPublicKey delegate,
		REAddr accountAddr,
		UInt384 amount
	) {
		this.delegate = delegate;
		this.accountAddr = accountAddr;
		this.shareAmount = amount;
	}

	public StakeOwnershipHoldingBucket withdrawOwnership(StakeOwnership stakeOwnership) throws NotEnoughResourcesException, MismatchException {
		if (!delegate.equals(stakeOwnership.getDelegateKey())) {
			throw new MismatchException("Shares must be from same delegate");
		}
		if (!stakeOwnership.getOwner().equals(accountAddr)) {
			throw new MismatchException("Shares must be for same account");
		}
		var withdraw384 = UInt384.from(stakeOwnership.getAmount());
		if (shareAmount.compareTo(withdraw384) < 0) {
			throw new NotEnoughResourcesException(stakeOwnership.getAmount(), shareAmount.getLow());
		}

		return new StakeOwnershipHoldingBucket(delegate, accountAddr, shareAmount.subtract(withdraw384));
	}

	public StakeOwnershipHoldingBucket depositOwnership(StakeOwnership stakeOwnership) throws MismatchException {
		if (!delegate.equals(stakeOwnership.getDelegateKey())) {
			throw new MismatchException("Shares must be from same delegate");
		}
		if (!stakeOwnership.getOwner().equals(accountAddr)) {
			throw new MismatchException("Shares must be for same account");
		}
		return new StakeOwnershipHoldingBucket(delegate, accountAddr, UInt384.from(stakeOwnership.getAmount()).add(shareAmount));
	}

	public StakeOwnershipHoldingBucket unstake(PreparedUnstakeOwnership u) throws NotEnoughResourcesException, MismatchException {
		if (!Objects.equals(accountAddr, u.getOwner())) {
			throw new MismatchException("Must unstake to self");
		}

		var unstakeAmount = UInt384.from(u.getAmount());
		if (shareAmount.compareTo(unstakeAmount) < 0) {
			throw new NotEnoughResourcesException(u.getAmount(), shareAmount.getLow());
		}

		return new StakeOwnershipHoldingBucket(
			delegate,
			accountAddr,
			shareAmount.subtract(unstakeAmount)
		);
	}

	public void destroy(ExecutionContext context, ImmutableAddrs immutableAddrs) throws ProcedureException {
		if (!shareAmount.isZero()) {
			throw new ProcedureException("Shares cannot be burnt.");
		}
	}
}
