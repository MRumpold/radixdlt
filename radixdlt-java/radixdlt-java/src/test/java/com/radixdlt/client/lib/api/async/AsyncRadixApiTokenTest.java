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
package com.radixdlt.client.lib.api.async;

import org.junit.Test;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncRadixApiTokenTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NATIVE_TOKEN = "{\"result\":{\"tokenInfoURL\":\"https://tokens.radixdlt.com/\","
		+ "\"symbol\":\"xrd\",\"isSupplyMutable\":true,\"granularity\":\"1\",\"name\":"
		+ "\"Rads\",\"rri\":\"xrd_rb1qya85pwq\","
		+ "\"description\":\"Radix Betanet Tokens\",\"currentSupply\":"
		+ "\"8000000000000000000000000000000000000000000000\",\"iconURL\":"
		+ "\"https://assets.radixdlt.com/icons/icon-xrd-32x32.png\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testNativeToken() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.token().describeNative()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName()))
				.join())
			.join();
	}

	@Test
	public void testTokenInfo() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.token().describe("xrd_rb1qya85pwq")
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName()))
				.join())
			.join();
	}

	private Promise<RadixApi> prepareClient(String responseBody) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(responseBody);
		when(client.<String>sendAsync(any(), any())).thenReturn(completableFuture);

		try {
			return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
		} finally {
			completableFuture.completeAsync(() -> response);
		}
	}

	private static ECKeyPair keyPairOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];

		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}
}
