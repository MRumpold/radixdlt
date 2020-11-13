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
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages dispatching of internal events to a given environment
 * TODO: Move all other events into this module
 */
public class DispatcherModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();
	@Override
	public void configure() {
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalSyncRequest>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Timeout>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<EpochView>>() { }, ProcessOnDispatch.class);
	}

	@Provides
	private EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<LocalSyncRequest>> syncProcessors,
		Environment environment
	) {
		EventDispatcher<LocalSyncRequest> envDispatcher = environment.getDispatcher(LocalSyncRequest.class);
		return req -> {
			Class<?> callingClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
			logger.info("LOCAL_SYNC_REQUEST dispatched by {}", callingClass);
			syncProcessors.forEach(e -> e.process(req));
			envDispatcher.dispatch(req);
		};
	}

	@Provides
	private ScheduledEventDispatcher<LocalGetVerticesRequest> localGetVerticesRequestRemoteEventDispatcher(Environment environment) {
		return environment.getScheduledDispatcher(LocalGetVerticesRequest.class);
	}

	@Provides
	private EventDispatcher<EpochView> viewEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<EpochView>> processors,
		Environment environment
	) {
		EventDispatcher<EpochView> dispatcher = environment.getDispatcher(EpochView.class);
		return epochView -> {
			processors.forEach(e -> e.process(epochView));
			dispatcher.dispatch(epochView);
		};
	}

	@Provides
	private EventDispatcher<Timeout> timeoutEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<Timeout>> processors,
		Environment environment
	) {
		EventDispatcher<Timeout> dispatcher = environment.getDispatcher(Timeout.class);
		return timeout -> {
			logger.warn("LOCAL_TIMEOUT dispatched: {}", timeout);
			processors.forEach(e -> e.process(timeout));
			dispatcher.dispatch(timeout);
		};
	}
}
