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

package com.radixdlt.atom.actions;

import com.radixdlt.atom.TxAction;
import com.radixdlt.crypto.ECPublicKey;


public final class SystemNextView implements TxAction {
	private final long view;
	private final long timestamp;
	private final ECPublicKey leader;

	public SystemNextView(long view, long timestamp, ECPublicKey leader) {
		this.view = view;
		this.timestamp = timestamp;
		this.leader = leader;
	}

	public long view() {
		return view;
	}

	public long timestamp() {
		return timestamp;
	}

	public ECPublicKey leader() {
		return leader;
	}
}
