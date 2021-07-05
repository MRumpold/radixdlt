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

package com.radixdlt.application.validators.construction;

import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.UpdateValidatorMetadata;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.HashUtils;

import java.util.List;
import java.util.Optional;

public final class UpdateValidatorConstructor implements ActionConstructor<UpdateValidatorMetadata> {
	@Override
	public void construct(UpdateValidatorMetadata action, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swap(
			ValidatorMetaData.class,
			p -> p.getValidatorKey().equals(action.validatorKey()),
			Optional.of(SubstateWithArg.noArg(new ValidatorMetaData(action.validatorKey(), "", ""))),
			() -> new TxBuilderException("Invalid state.")
		).with(
			substateDown -> List.of(new ValidatorMetaData(
				action.validatorKey(),
				action.name() == null ? substateDown.getName() : action.name(),
				action.name() == null ? substateDown.getUrl() : action.url()
			))
		);
		txBuilder.end();
		var forkVoteHash = action.forkVoteHash();
		if (forkVoteHash.isPresent()) {
			txBuilder.down(
				ValidatorSystemMetadata.class,
				p -> p.getValidatorKey().equals(action.validatorKey()),
				Optional.of(SubstateWithArg.noArg(new ValidatorSystemMetadata(action.validatorKey(), HashUtils.zero256().asBytes()))),
				() -> new TxBuilderException("Could not find state")
			);
			txBuilder.up(new ValidatorSystemMetadata(action.validatorKey(), forkVoteHash.get().asBytes()));
		}
		txBuilder.end();
	}
}
