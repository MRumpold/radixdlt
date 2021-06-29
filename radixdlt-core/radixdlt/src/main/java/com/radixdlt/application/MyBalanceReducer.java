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

package com.radixdlt.application;

import com.google.inject.Inject;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Balance reducer for local node
 */
public final class MyBalanceReducer implements StateReducer<MyBalances> {
	private final REAddr addr;

	@Inject
	public MyBalanceReducer(@Self REAddr addr) {
		this.addr = Objects.requireNonNull(addr);
	}

	@Override
	public Class<MyBalances> stateClass() {
		return MyBalances.class;
	}

	@Override
	public Set<Class<? extends Particle>> particleClasses() {
		return Set.of(TokensInAccount.class);
	}

	@Override
	public Supplier<MyBalances> initial() {
		return MyBalances::new;
	}

	@Override
	public BiFunction<MyBalances, Particle, MyBalances> outputReducer() {
		return (balance, p) -> {
			var tokens = (TokensInAccount) p;
			if (tokens.getHoldingAddr().equals(addr)) {
				return balance.add(tokens.getResourceAddr(), tokens.getAmount());
			}
			return balance;
		};
	}

	@Override
	public BiFunction<MyBalances, Particle, MyBalances> inputReducer() {
		return (balance, p) -> {
			var tokens = (TokensInAccount) p;
			if (tokens.getHoldingAddr().equals(addr)) {
				return balance.remove(tokens.getResourceAddr(), tokens.getAmount());
			}
			return balance;
		};
	}
}
