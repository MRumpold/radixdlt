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

package com.radixdlt.application.validators.state;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

public final class ValidatorMetaData implements Particle {
	private final ECPublicKey key;
	private final String name;
	private final String url;

	public ValidatorMetaData(ECPublicKey key) {
		this(key, "", "");
	}

	public ValidatorMetaData(
		ECPublicKey key,
		String name,
		String url
	) {
		this.key = Objects.requireNonNull(key);
		this.name = Objects.requireNonNull(name);
		this.url = Objects.requireNonNull(url);
	}

	public ECPublicKey getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.key, this.name, this.url);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ValidatorMetaData)) {
			return false;
		}
		final var that = (ValidatorMetaData) obj;
		return Objects.equals(this.key, that.key)
			&& Objects.equals(this.name, that.name)
			&& Objects.equals(this.url, that.url);
	}

	@Override
	public String toString() {
		return String.format(
			"%s[%s, %s]",
			getClass().getSimpleName(), getKey(), getUrl()
		);
	}
}
