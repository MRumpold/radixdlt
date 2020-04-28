/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.test;

import com.google.common.collect.ImmutableMap;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * A mirror of RadixCore's SystemCounters class, used to manage system counters exposed through the API.
 * Note that both SystemCounters.from and {@link CounterType} must be kept in sync with their core counterparts.
 */
public final class SystemCounters {
	private final Map<CounterType, Long> systemCounters;

	private SystemCounters(Map<CounterType, Long> systemCounters) {
		this.systemCounters = systemCounters;
	}

	/**
	 * Gets the value of the given {@link CounterType}
	 * @param counterType The counter type
	 * @return The value of that type
	 */
	public long get(CounterType counterType) {
		return systemCounters.get(counterType);
	}

	/**
	 * Extracts {@link SystemCounters} from the given {@link JSONObject} (e.g. "counters" in the "/api/system" endpoint).
	 * @param jsonCounters The {@link JSONObject} representing the counters
	 * @return The parsed instance of {@link SystemCounters}
	 */
	public static SystemCounters from(JSONObject jsonCounters) {
		ImmutableMap.Builder<CounterType, Long> systemCounters = ImmutableMap.builder();
		for (CounterType value : CounterType.values()) {
			try {
				String[] path = value.jsonPath().split("\\.");
				JSONObject parent = jsonCounters;
				for (int i = 0; i < path.length - 1; i++) {
					parent = parent.getJSONObject(path[i]);
				}
				long counterValue = parent.getLong(path[path.length - 1]);
				systemCounters.put(value, counterValue);
			} catch (JSONException e) {
				System.err.printf("failed to extract value for %s at '%s': %s%n", value, value.jsonPath(), e);
				systemCounters.put(value, 0L);
			}
		}
		return new SystemCounters(systemCounters.build());
	}

	/**
	 * A mirror of RadixCore's CounterType, representing internal system counters
	 */
	public enum CounterType {
		CONSENSUS_INDIRECT_PARENT("consensus.indirect_parent"),
		CONSENSUS_REJECTED("consensus.rejected"),
		CONSENSUS_SYNC_SUCCESS("consensus.sync_success"),
		CONSENSUS_SYNC_EXCEPTION("consensus.sync_exception"),
		CONSENSUS_TIMEOUT("consensus.timeout"),
		CONSENSUS_VERTEXSTORE_SIZE("consensus.vertexstore_size"),
		CONSENSUS_VIEW("consensus.view"),
		LEDGER_PROCESSED("ledger.processed"),
		LEDGER_STORED("ledger.stored"),
		MEMPOOL_COUNT("mempool.count"),
		MEMPOOL_MAXCOUNT("mempool.maxcount"),
		MESSAGES_INBOUND_BADSIGNATURE("messages.inbound.badsignature"),
		MESSAGES_INBOUND_DISCARDED("messages.inbound.discarded"),
		MESSAGES_INBOUND_PENDING("messages.inbound.pending"),
		MESSAGES_INBOUND_PROCESSED("messages.inbound.processed"),
		MESSAGES_INBOUND_RECEIVED("messages.inbound.received"),
		MESSAGES_OUTBOUND_ABORTED("messages.outbound.aborted"),
		MESSAGES_OUTBOUND_PENDING("messages.outbound.pending"),
		MESSAGES_OUTBOUND_PROCESSED("messages.outbound.processed"),
		MESSAGES_OUTBOUND_SENT("messages.outbound.sent");

		private final String jsonPath;

		CounterType(String jsonPath) {
			this.jsonPath = jsonPath;
		}

		private String jsonPath() {
			return this.jsonPath;
		}
	}
}
