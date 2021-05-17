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

package com.radixdlt;

import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.api.Controller;
import com.radixdlt.api.ValidatorApiModule;

import com.radixdlt.api.UniverseController;
import com.radixdlt.api.construction.ConstructApiModule;
import com.radixdlt.api.faucet.FaucetModule;
import com.radixdlt.network.p2p.PeerDiscoveryModule;
import com.radixdlt.network.p2p.PeerLivenessMonitorModule;
import com.radixdlt.network.p2p.P2PModule;
import com.radixdlt.api.system.SystemApiModule;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineStateComputerModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesFromPropertiesModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.radixdlt.mempool.MempoolConfig;
import org.radix.universe.system.LocalSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.api.chaos.chaos.ChaosModule;
import com.radixdlt.api.archive.ArchiveApiModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.keys.PersistedBFTKeyModule;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.network.messaging.MessagingModule;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.universe.UniverseModule;

/**
 * Module which manages everything in a single node
 */
public final class RadixNodeModule extends AbstractModule {
	private static final String API_PREFIX = "api.";
	private static final String API_SUFFIX = ".enable";
	private static final String API_ARCHIVE = API_PREFIX + "archive" + API_SUFFIX;
	private static final String API_CONSTRUCT = API_PREFIX + "construct" + API_SUFFIX;
	private static final String API_SYSTEM = API_PREFIX + "system" + API_SUFFIX;
	private static final String API_ACCOUNT = API_PREFIX + "account" + API_SUFFIX;
	private static final String API_VALIDATOR = API_PREFIX + "validator" + API_SUFFIX;
	private static final String API_UNIVERSE = API_PREFIX + "universe" + API_SUFFIX;
	private static final String API_FAUCET = API_PREFIX + "faucet" + API_SUFFIX;
	private static final String API_CHAOS = API_PREFIX + "chaos" + API_SUFFIX;
	private static final String API_HEALTH = API_PREFIX + "health" + API_SUFFIX;
	private static final String API_VERSION = API_PREFIX + "version" + API_SUFFIX;

	private static final Logger log = LogManager.getLogger();

	private final RuntimeProperties properties;

	public RadixNodeModule(RuntimeProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void configure() {
		bind(RuntimeProperties.class).toInstance(properties);

		// Consensus configuration
		// These cannot be changed without introducing possibilities of
		// going out of sync with consensus.
		bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(properties.get("bft.sync.patience", 200));
		// Default values mean that pacemakers will sync if they are within 5 views of each other.
		// 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
		bindConstant().annotatedWith(PacemakerTimeout.class).to(3000L);
		bindConstant().annotatedWith(PacemakerRate.class).to(1.1);
		bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);

		// Mempool configuration
		var mempoolMaxSize = properties.get("mempool.maxSize", 10000);
		install(MempoolConfig.asModule(mempoolMaxSize, 5, 60000, 60000, 100));

		// Sync configuration
		final long syncPatience = properties.get("sync.patience", 5000L);
		bind(SyncConfig.class).toInstance(SyncConfig.of(syncPatience, 10, 3000L));

		// Radix Engine configuration
		// These cannot be changed without introducing possible forks with
		// the network.
		// TODO: Move these deeper into radix engine.
		install(RadixEngineConfig.asModule(1, 100, 50));

		// System (e.g. time, random)
		install(new SystemModule());

		install(new RxEnvironmentModule());

		install(new DispatcherModule());


		// Application
		install(new NodeApplicationModule());

		// API
//		if (properties.get())


		if (properties.get("validator_api.enable", false)) {
			log.info("Enabling /validator API");
			install(new ValidatorApiModule());
		}

		if (properties.get("archive_api.enable", false)) {
			log.info("Enabling Archive API");
			install(new ArchiveApiModule());
		}

		if (properties.get("construct_api.enable", false)) {
			log.info("Enabling Construct API");
			install(new ConstructApiModule());
		}

		if (properties.get("system_api.enable", false)) {
			log.info("Enabling System API");
			//TODO: finish it
			install(new SystemApiModule());
		}

		if (properties.get("account_api.enable", false)) {
			log.info("Enabling Account API");
			//TODO: finish it
			//install(new AccountApiModule());
		}

		if (properties.get("universe_api.enable", false)) {
			log.info("Enabling Universe API");
			var controllers = Multibinder.newSetBinder(binder(), Controller.class);
			controllers.addBinding().to(UniverseController.class).in(Scopes.SINGLETON);
		}

		if (properties.get("faucet.enable", false)) {
			//TODO: disable on mainnet
			log.info("Enabling faucet API");
			install(new FaucetModule());
		}

		if (properties.get("chaos.enable", false)) {
			//TODO: disable on mainnet
			log.info("Enabling chaos API");
			install(new ChaosModule());
		}

		// Consensus
		install(new PersistedBFTKeyModule());
		install(new CryptoModule());
		install(new ConsensusModule());

		// Ledger
		install(new LedgerModule());
		install(new MempoolReceiverModule());

		// Mempool Relay
		install(new MempoolRelayerModule());

		// Sync
		install(new SyncServiceModule());

		// Epochs - Consensus
		install(new EpochsConsensusModule());
		// Epochs - Ledger
		install(new EpochsLedgerUpdateModule());
		// Epochs - Sync
		install(new EpochsSyncModule());

		// State Computer
		install(new BetanetForksModule());
		if (properties.get("overwrite_forks.enable", false)) {
			log.info("Enabling fork overwrites");
			install(new ForkOverwritesFromPropertiesModule());
		} else {
			install(new RadixEngineForksModule());
		}
		install(new RadixEngineStateComputerModule());
		install(new RadixEngineModule());
		install(new RadixEngineStoreModule());

		// Checkpoints
		install(new RadixEngineCheckpointModule());
		install(new UniverseModule());

		// Storage
		install(new DatabasePropertiesModule());
		install(new PersistenceModule());
		install(new ConsensusRecoveryModule());
		install(new LedgerRecoveryModule());

		// System Info
		install(new SystemInfoModule());

		// Network
		install(new MessagingModule());
		install(new MessageCentralModule(properties));
		install(new HostIpModule(properties));
		install(new P2PModule(properties));
		install(new PeerDiscoveryModule());
		install(new PeerLivenessMonitorModule());
	}

	@Provides
	@Singleton
	LocalSystem localSystem(@Self BFTNode self, InfoSupplier infoSupplier) {
		return LocalSystem.create(self, infoSupplier);
	}
}
