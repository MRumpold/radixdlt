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

package com.radixdlt.api.service;

import com.google.inject.Inject;
import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.reducer.AllValidatorsReducer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.store.EngineStore;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.utils.functional.FunctionalUtils;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Result.Mapper2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.radixdlt.api.data.ApiErrors.UNKNOWN_VALIDATOR;
import static com.radixdlt.utils.functional.FunctionalUtils.skipUntil;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ValidatorInfoService {
	private final EngineStore<LedgerAndBFTProof> entryStore;
	private final TreeMap<Long, ForkConfig> epochToForkConfig;
	private final InMemorySystemInfo inMemorySystemInfo;

	@Inject
	public ValidatorInfoService(
		EngineStore<LedgerAndBFTProof> entryStore,
		TreeMap<Long, ForkConfig> epochToForkConfig,
		InMemorySystemInfo inMemorySystemInfo
	) {
		this.entryStore = entryStore;
		this.epochToForkConfig = epochToForkConfig;
		this.inMemorySystemInfo = inMemorySystemInfo;
	}

	public Mapper2<Optional<ECPublicKey>, List<ValidatorInfoDetails>> getValidators(int size, Optional<ECPublicKey> cursor) {
		var result = getAllValidators();
		var paged = cursor
			.map(key -> skipUntil(result, v -> v.getValidatorKey().equals(key)))
			.orElse(result);

		var list = paged.stream().limit(size).collect(Collectors.toList());
		var newCursor = list.stream().reduce(FunctionalUtils::findLast).map(ValidatorInfoDetails::getValidatorKey);

		return () -> Result.ok(tuple(newCursor, list));
	}

	public long getValidatorsCount() {
		return getAllValidators().size();
	}

	public Result<ValidatorInfoDetails> getValidator(ECPublicKey validatorPublicKey) {
		return getAllValidators()
			.stream()
			.filter(validatorInfoDetails -> validatorInfoDetails.getValidatorKey().equals(validatorPublicKey))
			.findFirst()
			.map(Result::ok)
			.orElseGet(() -> UNKNOWN_VALIDATOR.with(ValidatorAddress.of(validatorPublicKey)).result());
	}

	public List<ValidatorInfoDetails> getAllValidators() {
		var reducer = new AllValidatorsReducer();

		var validators = entryStore.reduceUpParticles(
			reducer.initial().get(),
			reducer.outputReducer(),
			retrieveEpochParser(),
			asArray(reducer.particleClasses())
		);

		var result = validators.map(ValidatorInfoDetails::create);
		result.sort(Comparator.comparing(ValidatorInfoDetails::getTotalStake).reversed());

		return result;
	}

	private SubstateDeserialization retrieveEpochParser() {
		return epochToForkConfig
			.floorEntry(inMemorySystemInfo.getCurrentProof().getEpoch())
			.getValue()
			.getParser()
			.getSubstateDeserialization();
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Particle>[] asArray(Set<Class<? extends Particle>> particleClasses) {
		return particleClasses.toArray(new Class[particleClasses.size()]);
	}
}
