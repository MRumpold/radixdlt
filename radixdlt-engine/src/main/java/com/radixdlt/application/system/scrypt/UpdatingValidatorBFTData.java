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

package com.radixdlt.application.system.scrypt;

import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.crypto.ECPublicKey;

import java.util.TreeMap;

public class UpdatingValidatorBFTData implements ReducerState {
	private final long maxRounds;
	private final TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate;
	private long expectedNextView;

	UpdatingValidatorBFTData(long maxRounds, long view, TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate) {
		this.maxRounds = maxRounds;
		this.expectedNextView = view;
		this.validatorsToUpdate = validatorsToUpdate;
	}

	private void incrementViews(long count) throws ProcedureException {
		if (this.expectedNextView + count < this.expectedNextView) {
			throw new ProcedureException("View overflow");
		}

		if (this.expectedNextView + count > maxRounds) {
			throw new ProcedureException("Max rounds is " + maxRounds + " but attempting to execute "
				+ (this.expectedNextView + count));
		}

		this.expectedNextView += count;
	}

	public ReducerState update(ValidatorBFTData next) throws ProcedureException {
		var first = validatorsToUpdate.firstKey();
		if (!next.validatorKey().equals(first)) {
			throw new ProcedureException("Invalid key for validator bft data update");
		}
		var old = validatorsToUpdate.remove(first);
		if (old.proposalsCompleted() > next.proposalsCompleted()
			|| old.proposalsMissed() > next.proposalsMissed()) {
			throw new ProcedureException("Invalid data for validator bft data update");
		}

		if (old.proposalsCompleted() == next.proposalsCompleted() && old.proposalsMissed() == next.proposalsMissed()) {
			throw new ProcedureException("No update to Validator BFT data");
		}

		var additionalProposalsCompleted = next.proposalsCompleted() - old.proposalsCompleted();
		var additionalProposalsMissed = next.proposalsMissed() - old.proposalsMissed();

		incrementViews(additionalProposalsCompleted);
		incrementViews(additionalProposalsMissed);

		if (!validatorsToUpdate.isEmpty()) {
			return this;
		}

		return new StartNextRound(this.expectedNextView);
	}
}
