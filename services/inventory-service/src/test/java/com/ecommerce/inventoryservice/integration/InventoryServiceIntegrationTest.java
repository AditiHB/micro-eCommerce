package com.ecommerce.inventoryservice.integration;

import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.common.inbox.ProcessedEventRepository;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.InventoryReservation;
import com.ecommerce.inventoryservice.InventoryReservationRepository;
import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.ecommerce.inventoryservice.service.InventorySagaHandler;
import com.ecommerce.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stock handling against a real PostgreSQL: the properties that a mock cannot prove - no overselling under
 * concurrency, the last unit being sellable, all-or-nothing multi-line reservations, exactly-once release, and
 * the saga handlers' atomicity with the outbox and inbox.
 */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Inventory (PostgreSQL)")
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventorySagaHandler saga;
    @Autowired
    private InventoryRepository inventory;
    @Autowired
    private InventoryReservationRepository reservations;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        processed.deleteAll();
        reservations.deleteAll();
        inventory.deleteAll();
    }

    private Inventory stock(String sku, int quantity) {
        return inventory.saveAndFlush(Inventory.builder().productId(sku).quantity(quantity).build());
    }

    private int quantityOf(String sku) {
        return inventory.findByProductId(sku).orElseThrow().getQuantity();
    }

    private static OrderCreatedEvent order(long orderId, String eventId, LineItem... lines) {
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, 7L, List.of(lines), new BigDecimal("100.00"), "USD");
        event.setEventId(eventId);
        return event;
    }

    private static LineItem line(String sku, int quantity) {
        return new LineItem(sku, quantity, new BigDecimal("10.00"));
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().map(o -> o.getEventType()).toList();
    }

    // ------------------------------------------------------------------ the last unit (A7)

    @Test
    @DisplayName("reserving exactly what is left succeeds and leaves zero - the last unit is sellable")
    void lastUnitIsSellable() {
        Inventory item = stock("SKU-LAST", 3);

        assertThat(inventoryService.reserveStock(item.getId(), 3).getQuantity()).isZero();
        assertThat(quantityOf("SKU-LAST")).isZero();
    }

    @Test
    @DisplayName("the saga reserves the last units of a product instead of failing on the zero")
    void sagaTakesTheLastUnits() {
        stock("SKU-LAST", 2);

        saga.onOrderCreated(order(1L, "evt-1", line("SKU-LAST", 2)));

        assertThat(quantityOf("SKU-LAST")).isZero();
        assertThat(outboxTypes()).containsExactly("inventory.reserved");
    }

    @Test
    @DisplayName("asking for more than is left is refused and changes nothing")
    void insufficientChangesNothing() {
        Inventory item = stock("SKU-1", 5);

        assertThatThrownBy(() -> inventoryService.reserveStock(item.getId(), 6)).isInstanceOf(ConflictException.class);

        assertThat(quantityOf("SKU-1")).isEqualTo(5);
    }

    @Test
    @DisplayName("the database refuses negative stock even if the application had a bug")
    void databaseRefusesNegativeStock() {
        stock("SKU-1", 5);

        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET quantity = -1 WHERE product_id = 'SKU-1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("concurrent reservations of a hot product never oversell: exactly the stock is handed out")
    void noOversellingUnderConcurrency() throws Exception {
        stock("SKU-HOT", 50);
        int orders = 80;
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            long orderId = 1000 + i;
            calls.add(() -> {
                saga.onOrderCreated(order(orderId, "evt-hot-" + orderId, line("SKU-HOT", 1)));
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(calls)) {
            f.get();
        }
        pool.shutdown();

        outbox.findAll().forEach(o -> {
            if (o.getEventType().equals("inventory.reserved")) {
                reserved.incrementAndGet();
            } else if (o.getEventType().equals("inventory.failed")) {
                refused.incrementAndGet();
            }
        });
        assertThat(reserved.get()).isEqualTo(50);
        assertThat(refused.get()).isEqualTo(30);
        assertThat(quantityOf("SKU-HOT")).isZero();
        assertThat(reservations.count()).isEqualTo(50);
    }

    // ------------------------------------------------------------------ all or nothing

    @Test
    @DisplayName("a multi-line order is reserved all-or-nothing: one short line reserves nothing at all")
    void allOrNothing() {
        stock("SKU-A", 10);
        stock("SKU-B", 1);

        saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 5), line("SKU-B", 2)));

        assertThat(quantityOf("SKU-A")).as("the line that WAS available is untouched").isEqualTo(10);
        assertThat(quantityOf("SKU-B")).isEqualTo(1);
        assertThat(reservations.count()).isZero();
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("inventory.failed");
            assertThat(row.getPayload()).contains("SKU-B").contains("requested 2").contains("available 1");
        });
    }

    @Test
    @DisplayName("an unknown product fails the whole order")
    void unknownProduct() {
        stock("SKU-A", 10);

        saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 1), line("SKU-NOPE", 1)));

        assertThat(quantityOf("SKU-A")).isEqualTo(10);
        assertThat(outbox.findAll()).singleElement().satisfies(row -> assertThat(row.getPayload()).contains("Unknown product SKU-NOPE"));
    }

    @Test
    @DisplayName("a satisfiable multi-line order takes every line and announces it, with the priced total carried through")
    void multiLineSuccess() {
        stock("SKU-A", 10);
        stock("SKU-B", 10);

        saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 3), line("SKU-B", 4)));

        assertThat(quantityOf("SKU-A")).isEqualTo(7);
        assertThat(quantityOf("SKU-B")).isEqualTo(6);
        assertThat(reservations.findByOrderId(1L)).extracting(InventoryReservation::getProductId).containsExactly("SKU-A", "SKU-B");
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("inventory.reserved");
            assertThat(row.getPayload()).contains("\"totalAmount\":100.00").contains("\"currency\":\"USD\"").contains("\"customerId\":7");
        });
    }

    @Test
    @DisplayName("an order whose lines can never be valid is dead-lettered at once, not retried")
    void invalidLinesAreNonRetryable() {
        stock("SKU-A", 10);

        assertThatThrownBy(() -> saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 0)))).isInstanceOf(NonRetryableEventException.class);
        assertThatThrownBy(() -> saga.onOrderCreated(order(2L, "evt-2"))).isInstanceOf(NonRetryableEventException.class);
        assertThat(quantityOf("SKU-A")).isEqualTo(10);
    }

    // ------------------------------------------------------------------ idempotency and compensation

    @Test
    @DisplayName("a redelivered order.created takes stock once")
    void duplicateOrderCreated() {
        stock("SKU-A", 10);
        OrderCreatedEvent event = order(1L, "evt-1", line("SKU-A", 3));

        saga.onOrderCreated(event);
        saga.onOrderCreated(event);

        assertThat(quantityOf("SKU-A")).isEqualTo(7);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelling returns exactly what the order held, once; a second cancellation changes nothing")
    void releaseIsExactlyOnce() {
        stock("SKU-A", 10);
        stock("SKU-B", 10);
        saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 3), line("SKU-B", 4)));

        saga.onOrderCancelled(EventSamples.orderCancelled(1L, "evt-cancel-1"));
        saga.onOrderCancelled(EventSamples.orderCancelled(1L, "evt-cancel-2")); // a second, different cancellation event

        assertThat(quantityOf("SKU-A")).isEqualTo(10);
        assertThat(quantityOf("SKU-B")).isEqualTo(10);
        assertThat(reservations.findByOrderId(1L)).allSatisfy(r -> assertThat(r.getReleasedAt()).isNotNull());
        assertThat(outboxTypes()).containsExactly("inventory.reserved", "inventory.released");
    }

    @Test
    @DisplayName("cancelling an order that holds no stock is a harmless no-op")
    void releaseNothing() {
        stock("SKU-A", 10);

        saga.onOrderCancelled(EventSamples.orderCancelled(99L, "evt-cancel-x"));

        assertThat(quantityOf("SKU-A")).isEqualTo(10);
        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("concurrent cancellations of one order give the stock back only once")
    void concurrentReleases() throws Exception {
        stock("SKU-A", 10);
        saga.onOrderCreated(order(1L, "evt-1", line("SKU-A", 4)));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String eventId = "evt-cancel-" + i;
            calls.add(() -> {
                saga.onOrderCancelled(EventSamples.orderCancelled(1L, eventId));
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(calls)) {
            f.get();
        }
        pool.shutdown();

        assertThat(quantityOf("SKU-A")).isEqualTo(10);
    }

    // ------------------------------------------------------------------ REST operations

    @Test
    @DisplayName("creating, reading and stock-taking an item; a duplicate product is a 409")
    void crud() {
        Long id = inventoryService.createInventory(CreateInventoryRequest.builder().productId("SKU-N").quantity(0).build()).getId();

        assertThat(inventoryService.getInventory(id).getQuantity()).isZero();
        assertThat(inventoryService.updateInventory(id, 25).getQuantity()).isEqualTo(25);
        assertThat(inventoryService.getInventory(id).getVersion()).isPositive();
        assertThatThrownBy(() -> inventoryService.createInventory(CreateInventoryRequest.builder().productId("SKU-N").quantity(1).build()))
                .isInstanceOf(ConflictException.class);
    }
}
