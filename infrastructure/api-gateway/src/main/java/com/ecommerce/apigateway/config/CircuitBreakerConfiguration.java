package com.ecommerce.apigateway.config;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class CircuitBreakerConfiguration implements RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker> {

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryAddedEvent) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
        log.info("Circuit Breaker registered: {}", circuitBreaker.getName());
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.warn("Circuit Breaker {} transitioned to {}", circuitBreaker.getName(), event.getStateTransition()))
            .onError(event -> log.error("Circuit Breaker {} recorded error: {}", circuitBreaker.getName(), event.getThrowable().getMessage()))
            .onSuccess(event -> log.debug("Circuit Breaker {} recorded success", circuitBreaker.getName()));
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryRemovedEvent) {
        log.info("Circuit Breaker removed: {}", entryRemovedEvent.getRemovedEntry().getName());
    }

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryReplacedEvent) {
        log.debug("Circuit Breaker replaced: {} -> {}",
            entryReplacedEvent.getOldEntry().getName(),
            entryReplacedEvent.getNewEntry().getName());
    }

    @Bean
    public RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker> circuitBreakerEventConsumer() {
        return this;
    }
}
