package com.ecommerce.common.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Tuning for the outbox relay ({@code ecommerce.outbox.*}). The defaults suit a single-digit-ms Kafka. */
@Getter
@Setter
@ConfigurationProperties(prefix = "ecommerce.outbox")
public class OutboxProperties {

    /** Switch the relay off (tests, or a service that only ever consumes). Events are still written. */
    private boolean relayEnabled = true;

    /** Pause between polls when the last poll found nothing. */
    private Duration pollInterval = Duration.ofMillis(250);

    /** Most events handed to Kafka per poll. */
    private int batchSize = 50;

    /** How long to wait for the broker's acknowledgement of one event. */
    private Duration sendTimeout = Duration.ofSeconds(10);

    /** Delay before the first retry of a failed event; doubles each attempt up to {@link #maxBackoff}. */
    private Duration initialBackoff = Duration.ofSeconds(1);

    private Duration maxBackoff = Duration.ofMinutes(1);

    /** After this many failed attempts an event is marked DEAD and an alert fires. */
    private int maxAttempts = 50;

    /** Published rows older than this are deleted (the event store keeps the permanent history). */
    private Duration retention = Duration.ofHours(24);
}
