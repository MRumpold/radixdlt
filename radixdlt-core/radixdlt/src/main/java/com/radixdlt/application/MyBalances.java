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

import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class MyBalances {
	private final Map<REAddr, UInt384> balances = new HashMap<>();

	public MyBalances add(REAddr rri, UInt256 amount) {
		balances.merge(rri, UInt384.from(amount), UInt384::add);
		return this;
	}

	public MyBalances remove(REAddr rri, UInt256 amount) {
		balances.computeIfPresent(rri, ((rriId1, uInt384) -> {
			var bal = uInt384.subtract(amount);
			return bal.isZero() ? null : bal;
		}));
		return this;
	}

	public void forEach(BiConsumer<REAddr, UInt384> consumer) {
		balances.forEach(consumer);
	}

	public Stream<Map.Entry<REAddr, UInt384>> stream() {
		return balances.entrySet().stream();
	}
}
