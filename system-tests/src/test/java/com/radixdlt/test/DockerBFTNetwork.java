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

import okhttp3.HttpUrl;
import okhttp3.Request;
import utils.CmdHelper;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DockerBFTNetwork implements Closeable, BFTNetwork {
	private static final String OPTIONS_KEY_PORT = "hostPort";

	private final int numNodes;
	private final String networkName;
	private final Map<String, Map<String, Object>> dockerOptionsPerNode;

	private DockerBFTNetwork(String networkName, int numNodes) {
		this.networkName = Objects.requireNonNull(networkName);
		this.numNodes = numNodes;

		this.dockerOptionsPerNode = this.setup();
	}

	// setup the network and prepare anything required to run it
	// this will also kill any other network with the same name (but not networks with a different name)
	private Map<String, Map<String, Object>> setup() {
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, numNodes);
		CmdHelper.removeAllDockerContainers(); // TODO do we need this? if yes, document it
		CmdHelper.runCommand("docker network rm " + networkName);
		CmdHelper.runCommand("docker network create " + networkName ,null, true);
		dockerOptionsPerNode.forEach((nodeId, options) -> {
			options.put("network", networkName);
			List<Object> dockerSetup = CmdHelper.node(options);
			String[] dockerEnv = (String[]) dockerSetup.get(0);
			String dockerCommand = (String) dockerSetup.get(1);
			CmdHelper.runCommand(dockerCommand, dockerEnv,true);
		});
		CmdHelper.checkNGenerateKey();

		return Collections.unmodifiableMap(dockerOptionsPerNode);
	}

	@Override
	public void close() {
		CmdHelper.removeAllDockerContainers();
		CmdHelper.runCommand("docker network rm " + networkName);
	}

	@Override
	public HttpUrl getEndpointUrl(String nodeId, String endpoint) {
		return HttpUrl.parse(getNodeEndpoint(nodeId, endpoint));
	}

	private String getNodeEndpoint(String nodeId, String endpoint) {
		return getNodeEndpoint(this.dockerOptionsPerNode.get(nodeId), endpoint);
	}

	// utility for getting the API endpoint (as a string) out of generated node options
	private static String getNodeEndpoint(Map<String, Object> nodeOptions, final String endpoint) {
		int nodePort = (Integer) nodeOptions.get(OPTIONS_KEY_PORT);

		return String.format("http://localhost:%d/%s", nodePort, endpoint);
	}

	@Override
	public Set<String> getNodeIds() {
		return this.dockerOptionsPerNode.keySet();
	}

	public String getNetworkName() {
		return this.networkName;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private static AtomicInteger networkIdCounter = new AtomicInteger(0);
		private String name = "test-network-" + networkIdCounter.getAndIncrement();
		private int numNodes = -1;

		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		public Builder numNodes(int numNodes) {
			if (numNodes < 1) {
				throw new IllegalArgumentException("numNodes must be >= 1 but was " + numNodes);
			}
			this.numNodes = numNodes;
			return this;
		}

		public DockerBFTNetwork build() {
			if (numNodes == -1) {
				throw new IllegalStateException("numNodes was not set");
			}

			return new DockerBFTNetwork(name, numNodes);
		}
	}
}
