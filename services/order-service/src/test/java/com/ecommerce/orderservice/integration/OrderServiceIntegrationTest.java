package com.ecommerce.orderservice.integration;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.eventsourcing.EventStoreRepository;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.common.inbox.ProcessedEventRepository;
import com.ecommerce.common.outbox.OutboxEvent;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.EventSamples;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.client.CatalogClient;
import com.ecommerce.orderservice.client.CatalogClient.ProductPrice;
import com.ecommerce.orderservice.client.CustomerDirectoryClient;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import com.ecommerce.orderservice.service.OrderPlacementService;
import com.ecommerce.orderservice.service.OrderSagaHandler;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * The order service against a real PostgreSQL with the real migrations: the aggregate, its atomicity with the
 * outbox, idempotent placement, the state machine and the saga handlers.
 */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Order service (PostgreSQL)")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderPlacementService placement;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderSagaHandler saga;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private EventStoreRepository eventStore;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private IdempotencyRecordRepository idempotency;

    @MockBean
    private CatalogClient catalog;
    @MockBean
    private CustomerDirectoryClient customers;
    @SpyBean
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        eventStore.deleteAll();
        processed.deleteAll();
        idempotency.deleteAll();
        orders.deleteAll();
        reset(publisherSpy());
        when(customers.exists(7L)).thenReturn(true);
        when(catalog.lookup(anyList())).thenReturn(Map.of(
                "SKU-001", new ProductPrice("SKU-001", "Headphones", new BigDecimal("79.99"), "USD"),
                "SKU-002", new ProductPrice("SKU-002", "Cable", new BigDecimal("12.99"), "USD")));
        caller("alice");
    }

    /**
     * The spy sits behind the transactional proxy; stub and reset the spy itself, or the proxy would demand a
     * transaction for the stubbing call.
     */
    private EventPublisher publisherSpy() {
        return AopTestUtils.getUltimateTargetObject(eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void caller(String name) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(name, "n/a"));
    }

    private static CreateOrderRequest request(CreateOrderRequest.Item... items) {
        return CreateOrderRequest.builder().customerId(7L).items(List.of(items)).build();
    }

    private static CreateOrderRequest.Item item(String sku, int quantity) {
        return new CreateOrderRequest.Item(sku, quantity);
    }

    private OrderResponse place(String key, CreateOrderRequest.Item... items) {
        return placement.placeOrder(request(items), key).order();
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().map(OutboxEvent::getEventType).toList();
    }

    // ------------------------------------------------------------------ the aggregate

    @Test
    @DisplayName("an order is stored with its priced lines, total and currency, and announces order.created")
    void placesAPricedOrder() {
        OrderResponse order = place(null, item("SKU-001", 2), item("SKU-002", 1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("172.97");
        assertThat(order.getItems()).extracting(OrderResponse.Line::getProductId).containsExactly("SKU-001", "SKU-002");
        assertThat(order.getItems().get(0).getLineTotal()).isEqualByComparingTo("159.98");

        Order stored = orders.findById(order.getId()).orElseThrow();
        assertThat(stored.getVersion()).isZero();
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("order.created");
            assertThat(row.getTopic()).isEqualTo("order-created");
            assertThat(row.getMessageKey()).isEqualTo(String.valueOf(order.getId()));
            assertThat(row.getPayload()).contains("\"totalAmount\":172.97").contains("SKU-001").contains("\"currency\":\"USD\"");
        });
        assertThat(eventStore.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the order and its event are one atomic write: if announcing fails, no order exists")
    void atomicWithTheOutbox() {
        doThrow(new IllegalStateException("outbox write failed")).when(publisherSpy()).publish(any(), any(), any());

        assertThatThrownBy(() -> place(null, item("SKU-001", 1))).isInstanceOf(IllegalStateException.class);

        assertThat(orders.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    @DisplayName("repeating a request with the same Idempotency-Key returns the original order - never a second one")
    void idempotentPlacement() {
        OrderService.Placement first = placement.placeOrder(request(item("SKU-001", 1)), "key-1");
        OrderService.Placement second = placement.placeOrder(request(item("SKU-001", 1)), "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.order().getId()).isEqualTo(first.order().getId());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(outboxTypes()).containsExactly("order.created");
    }

    @Test
    @DisplayName("the same key with a DIFFERENT request is an error, not a silent replay")
    void keyReuseIsRejected() {
        place("key-1", item("SKU-001", 1));

        assertThatThrownBy(() -> place("key-1", item("SKU-001", 5)))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting(e -> ((UnprocessableEntityException) e).getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("keys are per caller: two callers can use the same key without ever seeing each other's order")
    void keysAreScopedToTheCaller() {
        OrderResponse alices = place("same-key", item("SKU-001", 1));
        caller("bob");
        OrderResponse bobs = place("same-key", item("SKU-001", 1));

        assertThat(bobs.getId()).isNotEqualTo(alices.getId());
        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("two simultaneous requests with the same key create exactly one order")
    void concurrentDuplicatesCreateOneOrder() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<OrderService.Placement>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> {
                caller("carol");
                return placement.placeOrder(request(item("SKU-001", 1)), "race-key");
            });
        }
        List<Future<OrderService.Placement>> results = pool.invokeAll(calls);
        pool.shutdown();

        List<Long> ids = new ArrayList<>();
        for (Future<OrderService.Placement> result : results) {
            ids.add(result.get().order().getId());
        }
        assertThat(ids).containsOnly(ids.get(0));
        assertThat(orders.count()).isEqualTo(1);
        assertThat(outboxTypes()).containsExactly("order.created");
    }

    // ------------------------------------------------------------------ commands and the state machine

    @Test
    @DisplayName("cancelling announces order.cancelled once; cancelling again does nothing")
    void cancelIsIdempotent() {
        OrderResponse order = place(null, item("SKU-001", 1));

        assertThat(orderService.cancelOrder(order.getId(), "changed my mind").getStatus()).isEqualTo(OrderStatus.CANCELLED);
        orderService.cancelOrder(order.getId(), "again");

        assertThat(outboxTypes()).containsExactly("order.created", "order.cancelled");
        assertThat(orders.findById(order.getId()).orElseThrow().getVersion()).isPositive();
    }

    @Test
    @DisplayName("a completed order cannot be cancelled, and the failed attempt announces nothing")
    void completedOrderCannotBeCancelled() {
        OrderResponse order = place(null, item("SKU-001", 1));
        saga.onPaymentProcessed(EventSamples.paymentProcessed(order.getId(), "evt-pay-1"));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), "too late")).isInstanceOf(ConflictException.class);

        assertThat(outboxTypes()).containsExactly("order.created");
    }

    @Test
    @DisplayName("back office cannot complete an order by hand")
    void cannotCompleteByHand() {
        OrderResponse order = place(null, item("SKU-001", 1));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), OrderStatus.COMPLETED)).isInstanceOf(ConflictException.class);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("two writers holding the same version cannot both win: the second gets an optimistic-lock failure")
    void optimisticLocking() {
        OrderResponse order = place(null, item("SKU-001", 1));
        Order copyA = orders.findById(order.getId()).orElseThrow();
        Order copyB = orders.findById(order.getId()).orElseThrow();

        copyA.transitionTo(OrderStatus.INVENTORY_RESERVED);
        orders.saveAndFlush(copyA);
        copyB.transitionTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orders.saveAndFlush(copyB)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ------------------------------------------------------------------ saga handlers (with the real inbox)

    @Test
    @DisplayName("the happy path: reserved, then payment captured -> COMPLETED, with no further events")
    void happyPath() {
        OrderResponse order = place(null, item("SKU-001", 1));

        saga.onInventoryReserved(EventSamples.inventoryReserved(order.getId(), "evt-res-1"));
        assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        saga.onPaymentProcessed(EventSamples.paymentProcessed(order.getId(), "evt-pay-1"));

        assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(outboxTypes()).containsExactly("order.created");
    }

    @Test
    @DisplayName("a redelivered event (same event id) is applied once: the inbox makes the handler idempotent")
    void duplicateDeliveryIsHarmless() {
        OrderResponse order = place(null, item("SKU-001", 1));

        saga.onInventoryFailed(EventSamples.inventoryFailed(order.getId(), "evt-fail-1"));
        long versionAfterFirst = orders.findById(order.getId()).orElseThrow().getVersion();
        saga.onInventoryFailed(EventSamples.inventoryFailed(order.getId(), "evt-fail-1"));

        assertThat(orders.findById(order.getId()).orElseThrow().getVersion()).isEqualTo(versionAfterFirst);
        assertThat(outboxTypes()).containsExactly("order.created", "order.cancelled");
    }

    @Test
    @DisplayName("a failed handler rolls back completely - including its inbox claim - so the retry is processed")
    void failedHandlerLeavesNothingBehind() {
        OrderResponse order = place(null, item("SKU-001", 1));
        doThrow(new IllegalStateException("outbox unavailable")).when(publisherSpy()).publish(any(), any(), any());

        assertThatThrownBy(() -> saga.onInventoryFailed(EventSamples.inventoryFailed(order.getId(), "evt-fail-2")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).as("rolled back").isEqualTo(OrderStatus.PENDING);
        assertThat(processed.count()).as("the claim was rolled back too").isZero();

        reset(publisherSpy());
        saga.onInventoryFailed(EventSamples.inventoryFailed(order.getId(), "evt-fail-2")); // the retry
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("a late payment for a cancelled order does not revive it and re-announces the cancellation so it is refunded")
    void latePaymentIsRefunded() {
        OrderResponse order = place(null, item("SKU-001", 1));
        orderService.cancelOrder(order.getId(), "customer cancelled");

        saga.onPaymentProcessed(EventSamples.paymentProcessed(order.getId(), "evt-late-pay"));

        assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxTypes()).containsExactly("order.created", "order.cancelled", "order.cancelled");
    }

    @Test
    @DisplayName("the saga deadline cancels an order stuck in flight and announces it; fresh orders are left alone")
    void sagaDeadline() {
        OrderResponse stuck = place(null, item("SKU-001", 1));
        OrderResponse fresh = place(null, item("SKU-002", 1));

        boolean expired = orderService.expire(stuck.getId(), LocalDateTime.now().plusMinutes(1), "Saga timed out");
        boolean kept = orderService.expire(fresh.getId(), LocalDateTime.now().minusMinutes(5), "Saga timed out");

        assertThat(expired).isTrue();
        assertThat(kept).isFalse();
        assertThat(orderService.getOrder(stuck.getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderService.getOrder(fresh.getId()).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbox.findAll()).filteredOn(r -> r.getEventType().equals("order.cancelled")).hasSize(1);
    }

    // ------------------------------------------------------------------ reading

    @Test
    @DisplayName("listing is paged and scoped by customer")
    void listing() {
        place(null, item("SKU-001", 1));
        place(null, item("SKU-002", 1));

        assertThat(orderService.getAllOrders(0, 10, "id").getContent()).hasSize(2);
        assertThat(orderService.getOrdersByCustomer(7L, 0, 10, "createdAt").getContent()).hasSize(2);
        assertThat(orderService.getOrdersByCustomer(999L, 0, 10, "id").getContent()).isEmpty();
        assertThat(orderService.getAllOrders(0, 1, "id").getContent()).hasSize(1);
    }
}
