package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
public final class Vote {
	private final int hash;
	private final long round;

	public Vote(long round, int hash) {
		this.round = round;
		this.hash = hash;
	}

	public long getRound() {
		return round;
	}

	@Override
	public int hashCode() {
		return Objects.hash(round, hash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vote)) {
			return false;
		}

		Vote v = (Vote) o;
		return v.hash == this.hash && v.round == this.round;
	}
}
