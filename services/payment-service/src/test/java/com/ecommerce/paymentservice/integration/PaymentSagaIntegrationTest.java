package com.ecommerce.paymentservice.integration;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.InventoryReservedEvent;
import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.common.inbox.ProcessedEventRepository;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.exception.InvalidPaymentTransitionException;
import com.ecommerce.paymentservice.gateway.SimulatedPaymentGateway;
import com.ecommerce.paymentservice.service.PaymentSagaHandler;
import com.ecommerce.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Charging and refunding against a real PostgreSQL and the simulated processor: the order's own total is what is
 * charged (not a constant), a decline is an outcome, there is never more than one charge per order, and every
 * change is atomic with the event announcing it.
 */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Payment saga (PostgreSQL)")
class PaymentSagaIntegrationTest {

    @Autowired
    private PaymentSagaHandler saga;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @SpyBean
    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        processed.deleteAll();
        payments.deleteAll();
        reset(gateway);
    }

    private static InventoryReservedEvent reserved(long orderId, String eventId, String total) {
        InventoryReservedEvent event = new InventoryReservedEvent(orderId, 7L,
                List.of(new LineItem("SKU-001", 1, new BigDecimal(total))), new BigDecimal(total), "USD");
        event.setEventId(eventId);
        return event;
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().map(o -> o.getEventType()).toList();
    }

    // ------------------------------------------------------------------ charging

    @Test
    @DisplayName("the charge is the order's total from the event - not a hard-coded amount")
    void chargesTheOrderTotal() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "172.97"));

        Payment payment = payments.findByOrderId(1L).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getAmount()).isEqualByComparingTo("172.97");
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getCustomerId()).isEqualTo(7L);
        assertThat(payment.getProcessorReference()).startsWith("sim_cap_");
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("payment.processed");
            assertThat(row.getPayload()).contains("\"amount\":172.97").contains("\"customerId\":7").contains("\"orderId\":1");
        });
    }

    @Test
    @DisplayName("a real decline (amount over the limit) is payment.failed with the reason - not an exception")
    void declineIsAnnounced() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "25000.00"));

        Payment payment = payments.findByOrderId(1L).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("declined");
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("payment.failed");
            assertThat(row.getPayload()).contains("declined");
        });
    }

    @Test
    @DisplayName("a redelivered inventory.reserved charges once")
    void duplicateDeliveryChargesOnce() {
        InventoryReservedEvent event = reserved(1L, "evt-1", "50.00");

        saga.onInventoryReserved(event);
        saga.onInventoryReserved(event);

        assertThat(payments.count()).isEqualTo(1);
        assertThat(outboxTypes()).containsExactly("payment.processed");
    }

    @Test
    @DisplayName("two different events for the same order (a race) still produce exactly one charge")
    void raceChargesOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            InventoryReservedEvent event = reserved(7L, "evt-race-" + i, "50.00");
            calls.add(() -> {
                try {
                    saga.onInventoryReserved(event);
                } catch (RuntimeException lostTheRace) {
                    // the unique constraint on order_id stopped it: in production the retry finds the payment
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(calls)) {
            f.get();
        }
        pool.shutdown();

        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.findAll()).filteredOn(r -> r.getEventType().equals("payment.processed")).hasSize(1);
    }

    @Test
    @DisplayName("a processor outage rolls everything back - no payment, no event, no inbox claim - so the retry charges")
    void processorOutageRollsBack() {
        InventoryReservedEvent event = reserved(1L, "evt-1", "50.00");
        doThrow(new IllegalStateException("processor timed out")).when(gateway).authorize(anyString(), any(), anyString());

        assertThatThrownBy(() -> saga.onInventoryReserved(event)).hasMessage("processor timed out");

        assertThat(payments.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(processed.count()).isZero();

        reset(gateway); // the processor is back
        saga.onInventoryReserved(event); // the container's retry
        assertThat(payments.findByOrderId(1L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    @DisplayName("an event with no chargeable total is dead-lettered at once, not retried")
    void nothingToCharge() {
        InventoryReservedEvent free = reserved(1L, "evt-1", "0.00");

        assertThatThrownBy(() -> saga.onInventoryReserved(free)).isInstanceOf(NonRetryableEventException.class);
        assertThat(payments.count()).isZero();
    }

    // ------------------------------------------------------------------ compensation

    @Test
    @DisplayName("cancelling an order with a captured payment refunds it through the processor and announces refund.completed")
    void cancellationRefunds() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "172.97"));

        saga.onOrderCancelled(EventSamples.orderCancelled(1L, "evt-cancel-1"));

        assertThat(payments.findByOrderId(1L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outboxTypes()).containsExactly("payment.processed", "refund.completed");
    }

    @Test
    @DisplayName("a repeated cancellation refunds once - also when it is a different event, like a re-announced one")
    void refundHappensOnce() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "172.97"));

        saga.onOrderCancelled(EventSamples.orderCancelled(1L, "evt-cancel-1"));
        saga.onOrderCancelled(EventSamples.orderCancelled(1L, "evt-cancel-2"));

        assertThat(outbox.findAll()).filteredOn(r -> r.getEventType().equals("refund.completed")).hasSize(1);
    }

    @Test
    @DisplayName("cancelling an order that was never charged, or whose payment was declined, refunds nothing")
    void nothingToRefund() {
        saga.onOrderCancelled(EventSamples.orderCancelled(99L, "evt-cancel-x"));
        saga.onInventoryReserved(reserved(2L, "evt-2", "25000.00")); // declined
        saga.onOrderCancelled(EventSamples.orderCancelled(2L, "evt-cancel-y"));

        assertThat(payments.findByOrderId(2L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outboxTypes()).containsExactly("payment.failed");
    }

    // ------------------------------------------------------------------ REST refund

    @Test
    @DisplayName("back-office refund works once on a captured payment and is refused afterwards")
    void backOfficeRefund() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "10.00"));
        Long id = payments.findByOrderId(1L).orElseThrow().getId();

        assertThat(paymentService.refundPayment(id).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> paymentService.refundPayment(id)).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(outbox.findAll()).filteredOn(r -> r.getEventType().equals("refund.completed")).hasSize(1);
    }

    @Test
    @DisplayName("the database refuses a status that is not part of the lifecycle (the old PROCESSED is gone)")
    void databaseConstraint() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "10.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET status = 'PROCESSED'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("there is at most one payment per order, enforced by the database")
    void onePaymentPerOrder() {
        saga.onInventoryReserved(reserved(1L, "evt-1", "10.00"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO payments (order_id, amount, currency, status, version, created_at, updated_at) "
                        + "VALUES (1, 5.00, 'USD', 'PENDING', 0, now(), now())"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
