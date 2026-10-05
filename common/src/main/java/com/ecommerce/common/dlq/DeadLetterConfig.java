package com.ecommerce.common.dlq;

import com.ecommerce.common.events.DeadLetterQueueHandler;
import com.ecommerce.common.events.DlqPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Switches on dead-letter handling for a service that consumes events: the listener that parks failed
 * messages, the replay publisher and the operator API. Services that only produce events do not import it.
 */
@Configuration
@Import({DeadLetterQueueHandler.class, DlqPublisher.class, DeadLetterService.class, DeadLetterController.class})
public class DeadLetterConfig {
}
