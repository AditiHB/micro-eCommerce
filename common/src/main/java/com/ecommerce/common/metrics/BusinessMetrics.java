package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    // Business metrics
    private Counter orderValueCounter;
    private Counter totalRevenueCounter;
    private Counter inventoryTurnoverCounter;
    private Timer checkoutDurationTimer;
    private Timer paymentProcessingDurationTimer;

    public void initialize() {
        log.info("Initializing business metrics");

        // Revenue tracking
        totalRevenueCounter = Counter.builder("ecommerce.revenue.total")
                .description("Total revenue generated")
                .baseUnit("USD")
                .register(meterRegistry);

        orderValueCounter = Counter.builder("ecommerce.order.value")
                .description("Order value counter")
                .baseUnit("USD")
                .register(meterRegistry);

        inventoryTurnoverCounter = Counter.builder("ecommerce.inventory.turnover")
                .description("Inventory turnover rate")
                .register(meterRegistry);

        // Performance tracking
        checkoutDurationTimer = Timer.builder("ecommerce.checkout.duration")
                .description("Time taken to complete checkout")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        paymentProcessingDurationTimer = Timer.builder("ecommerce.payment.processing.duration")
                .description("Time taken to process payment")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("Business metrics initialized successfully");
    }

    public void recordOrderValue(double amount) {
        orderValueCounter.increment(amount);
        totalRevenueCounter.increment(amount);
        log.debug("Order recorded: ${}", amount);
    }

    public void recordInventoryTurnover() {
        inventoryTurnoverCounter.increment();
    }

    public Timer.Sample recordCheckoutDuration() {
        return Timer.start(meterRegistry);
    }

    public void stopCheckoutTimer(Timer.Sample sample) {
        sample.stop(checkoutDurationTimer);
    }

    public Timer.Sample recordPaymentProcessingDuration() {
        return Timer.start(meterRegistry);
    }

    public void stopPaymentProcessingTimer(Timer.Sample sample) {
        sample.stop(paymentProcessingDurationTimer);
    }

    public double getTotalRevenue() {
        if (totalRevenueCounter != null) {
            return totalRevenueCounter.count();
        }
        return 0.0;
    }
}
