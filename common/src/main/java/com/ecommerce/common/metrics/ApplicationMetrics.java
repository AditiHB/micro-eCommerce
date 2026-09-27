package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationMetrics {

    private final MeterRegistry meterRegistry;

    private Counter customersCreatedCounter;
    private Counter ordersCreatedCounter;
    private Counter paymentsProcessedCounter;
    private Counter inventoryReservedCounter;

    private Timer createCustomerTimer;
    private Timer createOrderTimer;
    private Timer processPaymentTimer;
    private Timer reserveInventoryTimer;

    private Counter authenticationFailureCounter;
    private Counter validationErrorCounter;
    private Counter businessExceptionCounter;

    public void initialize() {
        customersCreatedCounter = Counter.builder("ecommerce.customers.created")
                .description("Total number of customers created")
                .register(meterRegistry);

        ordersCreatedCounter = Counter.builder("ecommerce.orders.created")
                .description("Total number of orders created")
                .register(meterRegistry);

        paymentsProcessedCounter = Counter.builder("ecommerce.payments.processed")
                .description("Total number of payments processed")
                .register(meterRegistry);

        inventoryReservedCounter = Counter.builder("ecommerce.inventory.reserved")
                .description("Total number of inventory reservations")
                .register(meterRegistry);

        createCustomerTimer = Timer.builder("ecommerce.customer.creation.time")
                .description("Time taken to create a customer")
                .register(meterRegistry);

        createOrderTimer = Timer.builder("ecommerce.order.creation.time")
                .description("Time taken to create an order")
                .register(meterRegistry);

        processPaymentTimer = Timer.builder("ecommerce.payment.processing.time")
                .description("Time taken to process a payment")
                .register(meterRegistry);

        reserveInventoryTimer = Timer.builder("ecommerce.inventory.reservation.time")
                .description("Time taken to reserve inventory")
                .register(meterRegistry);

        authenticationFailureCounter = Counter.builder("ecommerce.auth.failures")
                .description("Total authentication failures")
                .register(meterRegistry);

        validationErrorCounter = Counter.builder("ecommerce.validation.errors")
                .description("Total validation errors")
                .register(meterRegistry);

        businessExceptionCounter = Counter.builder("ecommerce.business.exceptions")
                .description("Total business exceptions")
                .register(meterRegistry);
    }

    public void recordCustomerCreated() {
        customersCreatedCounter.increment();
    }

    public Timer.Sample recordCustomerCreationTime() {
        return Timer.start(meterRegistry);
    }

    public void stopCustomerCreationTimer(Timer.Sample sample) {
        sample.stop(createCustomerTimer);
    }

    public void recordOrderCreated() {
        ordersCreatedCounter.increment();
    }

    public Timer.Sample recordOrderCreationTime() {
        return Timer.start(meterRegistry);
    }

    public void stopOrderCreationTimer(Timer.Sample sample) {
        sample.stop(createOrderTimer);
    }

    public void recordPaymentProcessed() {
        paymentsProcessedCounter.increment();
    }

    public Timer.Sample recordPaymentProcessingTime() {
        return Timer.start(meterRegistry);
    }

    public void stopPaymentProcessingTimer(Timer.Sample sample) {
        sample.stop(processPaymentTimer);
    }

    public void recordInventoryReserved() {
        inventoryReservedCounter.increment();
    }

    public Timer.Sample recordInventoryReservationTime() {
        return Timer.start(meterRegistry);
    }

    public void stopInventoryReservationTimer(Timer.Sample sample) {
        sample.stop(reserveInventoryTimer);
    }

    public void recordAuthenticationFailure() {
        authenticationFailureCounter.increment();
    }

    public void recordValidationError() {
        validationErrorCounter.increment();
    }

    public void recordBusinessException() {
        businessExceptionCounter.increment();
    }
}
