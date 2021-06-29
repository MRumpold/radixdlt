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

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.HasEpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExittingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.PreparedOwnerUpdate;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.PreparedRakeUpdate;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.ShutdownAll;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class EpochUpdateConstraintScrypt implements ConstraintScrypt {
	private final long maxRounds;
	private final UInt256 rewardsPerProposal;
	private final long unstakingEpochDelay;
	private int minimumCompletedProposalsPercentage;

	public EpochUpdateConstraintScrypt(
		long maxRounds,
		UInt256 rewardsPerProposal,
		int minimumCompletedProposalsPercentage,
		long unstakingEpochDelay
	) {
		this.maxRounds = maxRounds;
		this.rewardsPerProposal = rewardsPerProposal;
		this.unstakingEpochDelay = unstakingEpochDelay;
		this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
	}

	public final class ProcessExittingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeSet<ExittingStake> exitting = new TreeSet<>(
			(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
		);

		ProcessExittingStake(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(ShutdownAll<ExittingStake> shutdownAll) throws ProcedureException {
			var expectedEpoch = updatingEpoch.prevEpoch.getEpoch() + 1;
			var expectedPrefix = new byte[Long.BYTES + 1];
			Longs.copyTo(expectedEpoch, expectedPrefix, 1);
			shutdownAll.verifyPostTypePrefixEquals(expectedPrefix);
			shutdownAll.iterator().forEachRemaining(exitting::add);
			return next();
		}

		public ReducerState unlock(TokensInAccount u) throws ProcedureException {
			var exit = exitting.first();
			exitting.remove(exit);
			if (exit.getEpochUnlocked() != updatingEpoch.prevEpoch.getEpoch() + 1) {
				throw new ProcedureException("Stake must still be locked.");
			}
			var expected = exit.unlock();
			if (!expected.equals(u)) {
				throw new ProcedureException("Expecting next state to be " + expected + " but was " + u);
			}

			return next();
		}

		public ReducerState next() {
			return exitting.isEmpty() ? new RewardingValidators(updatingEpoch) : this;
		}
	}

	private final class RewardingValidators implements ReducerState {
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, ValidatorBFTData> validatorBFTData = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake = new TreeMap<>(KeyComparator.instance());
		private final UpdatingEpoch updatingEpoch;

		RewardingValidators(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(ShutdownAll<ValidatorBFTData> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			var iter = i.iterator();
			while (iter.hasNext()) {
				var validatorEpochData = iter.next();
				if (validatorBFTData.containsKey(validatorEpochData.validatorKey())) {
					throw new ProcedureException("Already inserted " + validatorEpochData.validatorKey());
				}
				validatorBFTData.put(validatorEpochData.validatorKey(), validatorEpochData);
			}

			return next();
		}

		ReducerState next() {
			if (validatorBFTData.isEmpty()) {
				return new PreparingUnstake(updatingEpoch, curStake, preparingStake);
			}

			var k = validatorBFTData.firstKey();
			if (curStake.containsKey(k)) {
				throw new IllegalStateException();
			}
			var bftData = validatorBFTData.remove(k);
			if (bftData.proposalsCompleted() + bftData.proposalsMissed() == 0) {
				return next();
			}

			var percentageCompleted = bftData.proposalsCompleted() * 10000
				/ (bftData.proposalsCompleted() + bftData.proposalsMissed());

			// Didn't pass threshold, no rewards!
			if (percentageCompleted < minimumCompletedProposalsPercentage) {
				return next();
			}

			var nodeRewards = rewardsPerProposal.multiply(UInt256.from(bftData.proposalsCompleted()));
			if (nodeRewards.isZero()) {
				return next();
			}

			return new LoadingStake(k, validatorStakeData -> {
				int rakePercentage = validatorStakeData.getRakePercentage();
				final UInt256 rakedEmissions;
				if (rakePercentage != 0) {
					var rake = nodeRewards
						.multiply(UInt256.from(rakePercentage))
						.divide(UInt256.from(RAKE_MAX));
					var validatorOwner = validatorStakeData.getOwnerAddr();
					var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
					initStake.put(validatorOwner, rake);
					preparingStake.put(k, initStake);
					rakedEmissions = nodeRewards.subtract(rake);
				} else {
					rakedEmissions = nodeRewards;
				}
				curStake.put(k, validatorStakeData.addEmission(rakedEmissions));
				return next();
			});
		}
	}

	public static final class CreatingNextValidatorSet implements ReducerState {
		private final Set<ECPublicKey> validators = new HashSet<>();
		private final UpdatingEpoch updatingEpoch;

		CreatingNextValidatorSet(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		ReducerState nextValidator(ValidatorBFTData u) throws ProcedureException {
			if (validators.contains(u.validatorKey())) {
				throw new ProcedureException("Already in set: " + u.validatorKey());
			}
			if (u.proposalsCompleted() != 0) {
				throw new ProcedureException("Proposals completed must be 0");
			}
			validators.add(u.validatorKey());
			return this;
		}

		ReducerState nextEpoch(EpochData epochData) throws ProcedureException {
			return updatingEpoch.nextEpoch(epochData);
		}
	}

	public static final class UpdatingEpoch implements ReducerState {
		private final HasEpochData prevEpoch;

		UpdatingEpoch(HasEpochData prevEpoch) {
			this.prevEpoch = prevEpoch;
		}

		ReducerState nextEpoch(EpochData u) throws ProcedureException {
			if (u.getEpoch() != prevEpoch.getEpoch() + 1) {
				throw new ProcedureException("Invalid next epoch: " + u.getEpoch()
					+ " Expected: " + (prevEpoch.getEpoch() + 1));
			}
			return new StartingEpochRound();
		}
	}

	public static final class StartingEpochRound implements ReducerState {
	}


	private final class Unstaking implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<REAddr, UInt256> unstaking;
		private final Function<ValidatorStakeData, ReducerState> onDone;
		private ValidatorStakeData current;

		Unstaking(
			UpdatingEpoch updatingEpoch,
			ValidatorStakeData current,
			TreeMap<REAddr, UInt256> unstaking,
			Function<ValidatorStakeData, ReducerState> onDone
		) {
			this.updatingEpoch = updatingEpoch;
			this.current = current;
			this.unstaking = unstaking;
			this.onDone = onDone;
		}

		ReducerState exit(ExittingStake u) throws ProcedureException {
			var firstAddr = unstaking.firstKey();
			var ownershipUnstake = unstaking.remove(firstAddr);
			var epochUnlocked = updatingEpoch.prevEpoch.getEpoch() + unstakingEpochDelay + 1;
			var nextValidatorAndExit = current.unstakeOwnership(
				firstAddr, ownershipUnstake, epochUnlocked
			);
			this.current = nextValidatorAndExit.getFirst();
			var expectedExit = nextValidatorAndExit.getSecond();
			if (!u.equals(expectedExit)) {
				throw new ProcedureException("Invalid exit expected " + expectedExit + " but was " + u);
			}

			return unstaking.isEmpty() ? onDone.apply(current) : this;
		}
	}

	private final class PreparingUnstake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingUnstake = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;

		PreparingUnstake(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> curStake,
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
			this.preparingStake = preparingStake;
		}

		ReducerState unstakes(ShutdownAll<PreparedUnstakeOwnership> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			i.iterator().forEachRemaining(preparedUnstakeOwned ->
				preparingUnstake
					.computeIfAbsent(
						preparedUnstakeOwned.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedUnstakeOwned.getOwner(), preparedUnstakeOwned.getAmount(), UInt256::add)
			);
			return next();
		}

		ReducerState next() {
			if (preparingUnstake.isEmpty()) {
				return new PreparingStake(updatingEpoch, curStake, preparingStake);
			}

			var k = preparingUnstake.firstKey();
			var unstakes = preparingUnstake.remove(k);

			if (!curStake.containsKey(k)) {
				return new LoadingStake(k, validatorStake ->
					new Unstaking(updatingEpoch, validatorStake, unstakes, s -> {
						curStake.put(k, s);
						return next();
					})
				);
			} else {
				var validatorStake = curStake.get(k);
				return new Unstaking(updatingEpoch, validatorStake, unstakes, s -> {
					curStake.put(k, s);
					return next();
				});
			}
		}
	}

	private static final class Staking implements ReducerState {
		private ValidatorStakeData validatorStake;
		private final TreeMap<REAddr, UInt256> stakes;
		private final Function<ValidatorStakeData, ReducerState> onDone;

		Staking(ValidatorStakeData validatorStake, TreeMap<REAddr, UInt256> stakes, Function<ValidatorStakeData, ReducerState> onDone) {
			this.validatorStake = validatorStake;
			this.stakes = stakes;
			this.onDone = onDone;
		}

		ReducerState stake(StakeOwnership stakeOwnership) throws ProcedureException {
			if (!Objects.equals(validatorStake.getValidatorKey(), stakeOwnership.getDelegateKey())) {
				throw new ProcedureException("Invalid update");
			}
			var accountAddr = stakes.firstKey();
			if (!Objects.equals(stakeOwnership.getOwner(), accountAddr)) {
				throw new ProcedureException(stakeOwnership + " is not the first addr in " + stakes);
			}
			var stakeAmt = stakes.remove(accountAddr);
			var nextValidatorAndOwnership = validatorStake.stake(accountAddr, stakeAmt);
			this.validatorStake = nextValidatorAndOwnership.getFirst();
			var expectedOwnership = nextValidatorAndOwnership.getSecond();
			if (!Objects.equals(stakeOwnership, expectedOwnership)) {
				throw new ProcedureException(
					String.format("Amount (%s) does not match what is prepared (%s)", stakeOwnership.getAmount(), stakeAmt)
				);
			}
			return stakes.isEmpty() ? onDone.apply(this.validatorStake) : this;
		}
	}

	private static final class LoadingStake implements ReducerState {
		private final ECPublicKey key;
		private final Function<ValidatorStakeData, ReducerState> onDone;

		LoadingStake(ECPublicKey key, Function<ValidatorStakeData, ReducerState> onDone) {
			this.key = key;
			this.onDone = onDone;
		}

		ReducerState startUpdate(ValidatorStakeData stake) throws ProcedureException {
			if (!stake.getValidatorKey().equals(key)) {
				throw new ProcedureException("Invalid stake load");
			}
			return onDone.apply(stake);
		}

		@Override
		public String toString() {
			return String.format("%s{onDone: %s}", this.getClass().getSimpleName(), onDone);
		}
	}

	private static final class PreparingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;

		PreparingStake(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> curStake,
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.curStake = curStake;
			this.updatingEpoch = updatingEpoch;
			this.preparingStake = preparingStake;
		}

		ReducerState prepareStakes(ShutdownAll<PreparedStake> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			i.iterator().forEachRemaining(preparedStake ->
				preparingStake
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return next();
		}

		ReducerState next() {
			if (preparingStake.isEmpty()) {
				return new PreparingRakeUpdate(updatingEpoch, curStake);
			}

			var k = preparingStake.firstKey();
			var stakes = preparingStake.remove(k);
			if (!curStake.containsKey(k)) {
				return new LoadingStake(k, validatorStake ->
					new Staking(validatorStake, stakes, updated -> {
						curStake.put(k, updated);
						return this.next();
					})
				);
			} else {
				return new Staking(curStake.get(k), stakes, updated -> {
					curStake.put(k, updated);
					return this.next();
				});
			}
		}
	}

	private static final class ResetRakeUpdate implements ReducerState {
		private final PreparedRakeUpdate update;
		private final Supplier<ReducerState> next;

		ResetRakeUpdate(PreparedRakeUpdate update, Supplier<ReducerState> next) {
			this.update = update;
			this.next = next;
		}

		ReducerState reset(ValidatorRakeCopy rakeCopy) throws ProcedureException {
			if (!rakeCopy.getValidatorKey().equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			if (rakeCopy.getCurRakePercentage() != update.getNextRakePercentage()) {
				throw new ProcedureException("Rake percentage must match.");
			}

			return next.get();
		}
	}

	private static final class PreparingRakeUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate;
		private final TreeMap<ECPublicKey, PreparedRakeUpdate> preparingRakeUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingRakeUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		ReducerState prepareRakeUpdates(ShutdownAll<PreparedRakeUpdate> shutdownAll) throws ProcedureException {
			var expectedEpoch = updatingEpoch.prevEpoch.getEpoch() + 1;
			var expectedPrefix = new byte[Long.BYTES + 1];
			Longs.copyTo(expectedEpoch, expectedPrefix, 1);
			shutdownAll.verifyPostTypePrefixEquals(expectedPrefix);
			var iter = shutdownAll.iterator();
			while (iter.hasNext()) {
				var preparedRakeUpdate = iter.next();
				preparingRakeUpdates.put(preparedRakeUpdate.getValidatorKey(), preparedRakeUpdate);
			}
			return next();
		}

		ReducerState next() {
			if (preparingRakeUpdates.isEmpty()) {
				return new PreparingOwnerUpdate(updatingEpoch, validatorsToUpdate);
			}

			var k = preparingRakeUpdates.firstKey();
			var validatorUpdate = preparingRakeUpdates.remove(k);
			if (!validatorsToUpdate.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					var updatedValidator = validatorStake.setRakePercentage(validatorUpdate.getNextRakePercentage());
					validatorsToUpdate.put(k, updatedValidator);
					return new ResetRakeUpdate(validatorUpdate, this::next);
				});
			} else {
				var updatedValidator = validatorsToUpdate.get(k)
					.setRakePercentage(validatorUpdate.getNextRakePercentage());
				validatorsToUpdate.put(k, updatedValidator);
				return new ResetRakeUpdate(validatorUpdate, this::next);
			}
		}
	}

	private static final class ResetOwnerUpdate implements ReducerState {
		private final ECPublicKey validatorKey;
		private final Supplier<ReducerState> next;

		ResetOwnerUpdate(ECPublicKey validatorKey, Supplier<ReducerState> next) {
			this.validatorKey = validatorKey;
			this.next = next;
		}

		ReducerState reset(ValidatorOwnerCopy update) throws ProcedureException {
			if (!validatorKey.equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			return next.get();
		}
	}

	private static final class PreparingOwnerUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate;
		private final TreeMap<ECPublicKey, PreparedOwnerUpdate> preparingValidatorUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingOwnerUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		ReducerState prepareValidatorUpdate(ShutdownAll<PreparedOwnerUpdate> shutdownAll) throws ProcedureException {
			shutdownAll.verifyPostTypePrefixIsEmpty();
			var iter = shutdownAll.iterator();
			while (iter.hasNext()) {
				var preparedValidatorUpdate = iter.next();
				preparingValidatorUpdates.put(preparedValidatorUpdate.getValidatorKey(), preparedValidatorUpdate);
			}
			return next();
		}

		ReducerState next() {
			if (preparingValidatorUpdates.isEmpty()) {
				return new PreparingRegisteredUpdate(updatingEpoch, validatorsToUpdate);
			}

			var k = preparingValidatorUpdates.firstKey();
			var validatorUpdate = preparingValidatorUpdates.remove(k);
			if (!validatorsToUpdate.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					var updatedValidator = validatorStake.setOwnerAddr(validatorUpdate.getOwnerAddress());
					validatorsToUpdate.put(k, updatedValidator);
					return new ResetOwnerUpdate(k, this::next);
				});
			} else {
				var updatedValidator = validatorsToUpdate.get(k)
					.setOwnerAddr(validatorUpdate.getOwnerAddress());
				validatorsToUpdate.put(k, updatedValidator);
				return new ResetOwnerUpdate(k, this::next);
			}
		}
	}

	private static final class ResetRegisteredUpdate implements ReducerState {
		private final PreparedRegisteredUpdate update;
		private final Supplier<ReducerState> next;

		ResetRegisteredUpdate(PreparedRegisteredUpdate update, Supplier<ReducerState> next) {
			this.update = update;
			this.next = next;
		}

		ReducerState reset(ValidatorRegisteredCopy registeredCopy) throws ProcedureException {
			if (!registeredCopy.getValidatorKey().equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			if (registeredCopy.isRegistered() != update.isRegistered()) {
				throw new ProcedureException("Registered flags must match.");
			}

			return next.get();
		}
	}

	private static final class PreparingRegisteredUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate;
		private final TreeMap<ECPublicKey, PreparedRegisteredUpdate> preparingRegisteredUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingRegisteredUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		ReducerState prepareRegisterUpdates(ShutdownAll<PreparedRegisteredUpdate> shutdownAll) throws ProcedureException {
			shutdownAll.verifyPostTypePrefixIsEmpty();
			var iter = shutdownAll.iterator();
			while (iter.hasNext()) {
				var preparedRegisteredUpdate = iter.next();
				preparingRegisteredUpdates.put(preparedRegisteredUpdate.getValidatorKey(), preparedRegisteredUpdate);
			}
			return next();
		}

		ReducerState next() {
			if (preparingRegisteredUpdates.isEmpty()) {
				return validatorsToUpdate.isEmpty()
					? new CreatingNextValidatorSet(updatingEpoch)
					: new UpdatingValidatorStakes(updatingEpoch, validatorsToUpdate);
			}

			var k = preparingRegisteredUpdates.firstKey();
			var validatorUpdate = preparingRegisteredUpdates.remove(k);
			if (!validatorsToUpdate.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					var updatedValidator = validatorStake.setRegistered(validatorUpdate.isRegistered());
					validatorsToUpdate.put(k, updatedValidator);
					return new ResetRegisteredUpdate(validatorUpdate, this::next);
				});
			} else {
				var updatedValidator = validatorsToUpdate.get(k)
					.setRegistered(validatorUpdate.isRegistered());
				validatorsToUpdate.put(k, updatedValidator);
				return new ResetRegisteredUpdate(validatorUpdate, this::next);
			}
		}
	}

	private static final class UpdatingValidatorStakes implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;
		UpdatingValidatorStakes(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorStakeData> curStake) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
		}

		ReducerState updateStake(ValidatorStakeData stake) throws ProcedureException {
			var k = curStake.firstKey();
			if (!stake.getValidatorKey().equals(k)) {
				throw new ProcedureException("First key does not match.");
			}

			var expectedUpdate = curStake.remove(k);
			if (!stake.equals(expectedUpdate)) {
				throw new ProcedureException("Stake amount does not match Expected: " + expectedUpdate + " Actual: " + stake);
			}

			return curStake.isEmpty() ? new CreatingNextValidatorSet(updatingEpoch) : this;
		}
	}

	private static class AllocatingSystem implements ReducerState {
	}

	private void registerGenesisTransitions(Loader os) {
		// For Mainnet Genesis
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, EpochData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getEpoch() != 0) {
					throw new ProcedureException("First epoch must be 0.");
				}

				return ReducerResult.incomplete(new AllocatingSystem());
			}
		));
		os.procedure(new UpProcedure<>(
			AllocatingSystem.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("First view must be 0.");
				}
				return ReducerResult.complete();
			}
		));
	}

	private void epochUpdate(Loader os) {
		// Epoch Update
		os.procedure(new DownProcedure<>(
			EndPrevRound.class, EpochData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> {
				// TODO: Should move this authorization instead of checking epoch > 0
				if (d.getSubstate().getEpoch() > 0 && s.getClosedRound().getView() != maxRounds) {
					throw new ProcedureException("Must execute epoch update on end of round " + maxRounds
						+ " but is " + s.getClosedRound().getView());
				}

				return ReducerResult.incomplete(new UpdatingEpoch(d.getSubstate()));
			}
		));

		os.procedure(new ShutdownAllProcedure<>(
			ExittingStake.class, UpdatingEpoch.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> {
				var exittingStake = new ProcessExittingStake(s);
				return ReducerResult.incomplete(exittingStake.process(i));
			}
		));
		os.procedure(new UpProcedure<>(
			ProcessExittingStake.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.unlock(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			ValidatorBFTData.class, RewardingValidators.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.process(i))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwnership.class, PreparingUnstake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.unstakes(i))
		));
		os.procedure(new DownProcedure<>(
			LoadingStake.class, ValidatorStakeData.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> ReducerResult.incomplete(s.startUpdate(d.getSubstate()))
		));
		os.procedure(new UpProcedure<>(
			Unstaking.class, ExittingStake.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.exit(u))
		));
		os.procedure(new ShutdownAllProcedure<>(
			PreparedStake.class, PreparingStake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareStakes(i))
		));
		os.procedure(new ShutdownAllProcedure<>(
			PreparedRakeUpdate.class, PreparingRakeUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareRakeUpdates(i))
		));
		os.procedure(new UpProcedure<>(
			ResetRakeUpdate.class, ValidatorRakeCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedOwnerUpdate.class, PreparingOwnerUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareValidatorUpdate(i))
		));
		os.procedure(new UpProcedure<>(
			ResetOwnerUpdate.class, ValidatorOwnerCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedRegisteredUpdate.class, PreparingRegisteredUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareRegisterUpdates(i))
		));
		os.procedure(new UpProcedure<>(
			ResetRegisteredUpdate.class, ValidatorRegisteredCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));


		os.procedure(new UpProcedure<>(
			Staking.class, StakeOwnership.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.stake(u))
		));
		os.procedure(new UpProcedure<>(
			UpdatingValidatorStakes.class, ValidatorStakeData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.updateStake(u))
		));

		os.procedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.nextValidator(u))
		));

		os.procedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, EpochData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.nextEpoch(u))
		));

		os.procedure(new UpProcedure<>(
			StartingEpochRound.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("Epoch must start with view 0");
				}

				return ReducerResult.complete();
			}
		));
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				EpochData.class,
				SubstateTypeId.EPOCH_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new EpochData(epoch);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					buf.putLong(s.getEpoch());
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ValidatorStakeData.class,
				SubstateTypeId.VALIDATOR_STAKE_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var isRegistered = REFieldSerialization.deserializeBoolean(buf);
					var amount = REFieldSerialization.deserializeUInt256(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var ownership = REFieldSerialization.deserializeUInt256(buf);
					var rakePercentage = REFieldSerialization.deserializeInt(buf);
					var ownerAddress = REFieldSerialization.deserializeREAddr(buf);
					return ValidatorStakeData.create(delegate, amount, ownership, rakePercentage, ownerAddress, isRegistered);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeBoolean(buf, s.isRegistered());
					buf.put(s.getAmount().toByteArray());
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					buf.put(s.getTotalOwnership().toByteArray());
					buf.putInt(s.getRakePercentage());
					REFieldSerialization.serializeREAddr(buf, s.getOwnerAddr());
				},
				s -> s.equals(ValidatorStakeData.createVirtual(s.getValidatorKey()))
			)
		);
		os.substate(
			new SubstateDefinition<>(
				StakeOwnership.class,
				SubstateTypeId.STAKE_OWNERSHIP.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new StakeOwnership(delegate, owner, amount);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ExittingStake.class,
				SubstateTypeId.EXITTING_STAKE.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var epochUnlocked = REFieldSerialization.deserializeNonNegativeLong(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new ExittingStake(epochUnlocked, delegate, owner, amount);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					buf.putLong(s.getEpochUnlocked());
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		registerGenesisTransitions(os);

		// Epoch update
		epochUpdate(os);
	}
}
