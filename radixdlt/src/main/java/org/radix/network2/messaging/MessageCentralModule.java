package org.radix.network2.messaging;

import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;

import com.google.inject.Singleton;
import org.radix.network2.TimeSupplier;
import org.radix.network2.transport.FirstMatchTransportManager;
import org.radix.properties.RuntimeProperties;
import org.radix.time.Time;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

/**
 * Guice configuration for {@link MessageCentral} that includes a UDP
 * transport.
 */
public final class MessageCentralModule extends AbstractModule {

	private final MessageCentralConfiguration config;
	private final TimeSupplier timeSource;

	public MessageCentralModule(RuntimeProperties properties) {
		this(MessageCentralConfiguration.fromRuntimeProperties(properties), Time::currentTimestamp);
	}

	MessageCentralModule(MessageCentralConfiguration config, TimeSupplier timeSource) {
		this.config = Objects.requireNonNull(config);
		this.timeSource = Objects.requireNonNull(timeSource);
	}

	@Override
	protected void configure() {
		// The main target
		bind(new TypeLiteral<EventQueueFactory<MessageEvent>>() {}).toInstance(PriorityBlockingQueue::new);

		bind(MessageCentral.class).to(MessageCentralImpl.class).in(Singleton.class);

		// MessageCentral dependencies
		bind(MessageCentralConfiguration.class).toInstance(this.config);
		bind(TransportManager.class).to(FirstMatchTransportManager.class);
		bind(TimeSupplier.class).toInstance(this.timeSource);
	}
}
