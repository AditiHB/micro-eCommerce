package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Application Metrics - Technical KPIs
 *
 * Purpose: Track system-level performance metrics for operational monitoring.
 * These metrics help identify performance bottlenecks and system health issues.
 *
 * Metrics Types:
 * 1. Counters: Monotonically increasing numbers (never decrease)
 *    - customersCreatedCounter: Total customer registrations since startup
 *    - ordersCreatedCounter: Total orders placed
 *    - paymentsProcessedCounter: Total payment transactions
 *    - inventoryReservedCounter: Total inventory reservations
 *    - Error counters: Failures, validations, exceptions
 *
 * 2. Timers: Measure operation duration with statistics
 *    - createCustomerTimer: p50, p95, p99 percentiles of customer creation
 *    - createOrderTimer: Distribution of order creation time
 *    - processPaymentTimer: Payment processing duration
 *    - reserveInventoryTimer: Inventory reservation time
 *
 * How It Works:
 * 1. Metrics initialized on application startup (MetricsInitializer)
 * 2. Service methods call record*() to increment counters
 * 3. Service methods use Timer.Sample for duration tracking:
 *    - Timer.start() begins measurement
 *    - Perform operation
 *    - sample.stop(timer) records duration
 * 4. Micrometer collects and exposes metrics:
 *    - /actuator/metrics endpoint (JSON)
 *    - /actuator/prometheus endpoint (Prometheus format)
 * 5. Prometheus scrapes metrics every 15 seconds
 * 6. Grafana visualizes with dashboards
 *
 * Example Usage in Service:
 * @Service
 * public class OrderService {
 *     @Autowired
 *     private ApplicationMetrics metrics;
 *
 *     public Order createOrder(CreateOrderRequest request) {
 *         Timer.Sample sample = metrics.recordOrderCreationTime();
 *         try {
 *             Order order = orderRepository.save(...);
 *             metrics.recordOrderCreated();
 *             return order;
 *         } finally {
 *             metrics.stopOrderCreationTimer(sample);
 *         }
 *     }
 * }
 *
 * Prometheus Queries:
 * - rate(ecommerce_orders_created[5m]): Orders per second (5-min rate)
 * - ecommerce_order_creation_time: Order creation duration histogram
 * - ecommerce_auth_failures: Failed authentication attempts
 *
 * @see io.micrometer.core.instrument.Counter
 * @see io.micrometer.core.instrument.Timer
 */
@Component
@RequiredArgsConstructor
public class ApplicationMetrics {

    /**
     * MeterRegistry from Micrometer - Central registry for all metrics.
     * Auto-configured by Spring Boot with Prometheus exporter.
     *
     * Micrometer acts as facade:
     * - Abstracts metric collection
     * - Supports multiple backends (Prometheus, JMX, etc.)
     * - In this project: configured for Prometheus export
     */
    private final MeterRegistry meterRegistry;

    private Counter customersCreatedCounter;
    private Counter ordersCreatedCounter;
    private Counter paymentsProcessedCounter;
    private Counter inventoryReservedCounter;

    private Timer createCustomerTimer;
    private Timer createOrderTimer;
    private Timer processPaymentTimer;
    private Timer reserveInventoryTimer;

    // Error/failure counters for operational monitoring
    private Counter authenticationFailureCounter;
    private Counter validationErrorCounter;
    private Counter businessExceptionCounter;

    /**
     * Initialize all metrics.
     *
     * Called by MetricsInitializer on application startup.
     * Creates all Counter and Timer instances with Micrometer.
     *
     * Counter naming convention: ecommerce.{entity}.{action}
     * Timer naming convention: ecommerce.{entity}.{action}.time
     *
     * This separation allows:
     * - Easy discoverability (grep for "ecommerce.*")
     * - Prometheus aggregation (group by metric_name)
     * - Grafana dashboard queries (filter by metric prefix)
     */
    public void initialize() {
        // Customer lifecycle metrics
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

    /**
     * Record a customer creation event.
     * Call this method when customer registration completes successfully.
     * Increments the counter by 1 (monotonically increasing).
     */
    public void recordCustomerCreated() {
        customersCreatedCounter.increment();
    }

    /**
     * Start timing a customer creation operation.
     * Call at the beginning of createCustomer() method.
     * Returns Timer.Sample object to track elapsed time.
     *
     * Usage:
     *   Timer.Sample sample = metrics.recordCustomerCreationTime();
     *   try {
     *       // ... customer creation logic
     *   } finally {
     *       metrics.stopCustomerCreationTimer(sample);
     *   }
     */
    public Timer.Sample recordCustomerCreationTime() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop timing and record the customer creation operation.
     * Call after customer creation completes (success or failure).
     * Records duration in milliseconds to createCustomerTimer.
     *
     * Metrics captured:
     * - Count: How many operations
     * - Total time: Sum of all durations
     * - Max time: Longest operation
     * - Mean time: Average duration
     * - Percentiles: p50, p95, p99 (useful for SLA monitoring)
     */
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
