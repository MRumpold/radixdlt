package com.radixdlt.store.tree;

public class PMTKey {

	private byte[] key = null;

	public PMTKey(byte[] inputKey) {
		this.key = inputKey;
	}

    public Boolean isEmpty() {
		return this.key.length == 0;
	}

	public byte[] toByte() {
		return key;
	}

}
