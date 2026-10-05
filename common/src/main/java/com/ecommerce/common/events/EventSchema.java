package com.ecommerce.common.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the wire contract of one domain event: its logical type name, the topic it travels on and its
 * schema version.
 *
 * <p>The logical name - not the Java class name - is what goes in the message's type header, so producers and
 * consumers are decoupled from each other's class names and packages. Compatibility rules are in
 * {@code docs/EVENT_CONTRACTS.md}: a schema only ever changes by <em>adding</em> optional fields; anything
 * else is a new type name (for example {@code order.created.v2}) published alongside the old one.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventSchema {

    /** Logical event type, e.g. {@code order.created}. Stable forever once published. */
    String type();

    /** Kafka topic the event is published to. */
    String topic();

    /** Schema version of this type; bumped only for backwards-compatible additions. */
    int version() default 1;
}
