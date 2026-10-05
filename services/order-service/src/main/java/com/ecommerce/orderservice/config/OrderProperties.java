package com.ecommerce.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Order behaviour that operations may tune ({@code ecommerce.orders.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "ecommerce.orders")
public class OrderProperties {

    /**
     * How long an order may stay in a non-final status before the saga deadline cancels it (which also triggers
     * every compensation). Must comfortably exceed the saga's own retry budget.
     */
    private Duration sagaTimeout = Duration.ofMinutes(5);

    /** How often the saga deadline looks for stale orders. */
    private Duration reaperInterval = Duration.ofSeconds(30);

    /** Switch the saga deadline off (tests). */
    private boolean reaperEnabled = true;

    /** Reject {@code POST /orders} that carries no {@code Idempotency-Key}. Off by default; on for clients that retry. */
    private boolean idempotencyKeyRequired = false;

    /** How long idempotency keys are remembered. */
    private Duration idempotencyRetention = Duration.ofHours(24);
}
