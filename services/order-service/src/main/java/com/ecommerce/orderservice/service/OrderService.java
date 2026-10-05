package com.ecommerce.orderservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.idempotency.IdempotencyRecord;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every write to an order, as one transaction each: the change and the events that announce it are committed
 * together or not at all (the events go to the outbox - see {@code EventPublisher}). Nothing in here talks to
 * another service; looking up prices and customers happens before, in {@link OrderPlacementService}, so no
 * database connection is held while a remote call is in flight.
 *
 * <p>Workflow state is deliberately not cached: an order's status changes on every saga step, so a cache would
 * only ever be stale or constantly evicted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    /** Fields a caller may sort by; anything else is a 400 rather than an exception from the persistence layer. */
    static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "updatedAt", "status", "totalAmount", "customerId");

    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final EventPublisher eventPublisher;

    /** What a placement produced: the order, and whether it is a replay of an earlier identical request. */
    public record Placement(OrderResponse order, boolean replayed) {
    }

    /** A line of a new order, already priced from the catalogue. */
    public record PricedLine(String productId, int quantity, BigDecimal unitPrice) {
    }

    // ------------------------------------------------------------------ placing an order

    /** The order created earlier by this caller's key, if any - a replay if the request is the same, else an error. */
    @Transactional(readOnly = true)
    public Optional<Placement> findReplay(String principal, String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return idempotencyRepository.findByPrincipalAndIdempotencyKey(principal, idempotencyKey).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new UnprocessableEntityException(
                        "This Idempotency-Key was already used for a different request", "IDEMPOTENCY_KEY_REUSED");
            }
            Order order = orderRepository.findById(record.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", record.getOrderId()));
            return new Placement(mapToResponse(order), true);
        });
    }

    /**
     * Creates the order, remembers the idempotency key and queues {@code order.created}, atomically. If two
     * requests with the same key race, the loser's insert violates the unique constraint and this whole
     * transaction rolls back - the caller then replays the winner's order.
     */
    public Placement place(Long customerId, String currency, List<PricedLine> lines,
                           String principal, String idempotencyKey, String requestHash) {
        Order order = Order.place(customerId, currency, lines.stream()
                .map(l -> OrderLine.builder().productId(l.productId()).quantity(l.quantity()).unitPrice(l.unitPrice()).build())
                .toList());
        orderRepository.saveAndFlush(order);

        if (idempotencyKey != null) {
            idempotencyRepository.saveAndFlush(IdempotencyRecord.builder()
                    .principal(principal).idempotencyKey(idempotencyKey).requestHash(requestHash)
                    .orderId(order.getId()).createdAt(Instant.now()).build());
        }

        eventPublisher.publish(new OrderCreatedEvent(order.getId(), order.getCustomerId(), eventLines(order),
                order.getTotalAmount(), order.getCurrency()));
        log.info("Order {} placed for customer {}: {} lines, total {} {}",
                order.getId(), customerId, order.getLines().size(), order.getTotalAmount(), order.getCurrency());
        return new Placement(mapToResponse(order), false);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return mapToResponse(orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(int pageNumber, int pageSize, String sortBy) {
        Pageable pageable = pageable(pageNumber, pageSize, sortBy);
        Page<Order> page = orderRepository.findAll(pageable);
        return PagedResponse.of(page.getContent().stream().map(this::mapToResponse).toList(),
                pageNumber, pageable.getPageSize(), page.getTotalElements());
    }

    /** One customer's orders - what a plain USER gets from {@code GET /orders}. */
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByCustomer(Long customerId, int pageNumber, int pageSize, String sortBy) {
        Pageable pageable = pageable(pageNumber, pageSize, sortBy);
        Page<Order> page = orderRepository.findByCustomerId(customerId, pageable);
        return PagedResponse.of(page.getContent().stream().map(this::mapToResponse).toList(),
                pageNumber, pageable.getPageSize(), page.getTotalElements());
    }

    // ------------------------------------------------------------------ commands

    /**
     * Cancels an order. Cancelling is a command, not a field edit: it moves the order to CANCELLED <em>and</em>
     * announces {@code order.cancelled}, which is what makes inventory release its stock and payment refund a
     * captured charge. Cancelling an already-cancelled order is a harmless no-op (idempotent); a completed or
     * failed order cannot be cancelled (409).
     */
    public OrderResponse cancelOrder(Long id, String reason) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return mapToResponse(order);
        }
        cancel(order, reason, null);
        return mapToResponse(order);
    }

    /**
     * Back-office status change. Only the edges back office is entitled to are open here: cancelling (through
     * the cancel command, so the compensations run) and marking an order failed. Everything else belongs to the
     * saga - completing an order by hand would skip payment - and is refused with 409.
     */
    public OrderResponse updateOrderStatus(Long id, OrderStatus target) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        switch (target) {
            case CANCELLED -> {
                if (order.getStatus() != OrderStatus.CANCELLED) {
                    cancel(order, "Cancelled by back office", null);
                }
            }
            case FAILED -> order.transitionTo(OrderStatus.FAILED);
            default -> throw new ConflictException(
                    "Status " + target + " is set by the order saga and cannot be set by hand", "ORDER_STATUS_MANAGED_BY_SAGA");
        }
        return mapToResponse(order);
    }

    /** Saga deadline: cancels an order that has been in flight too long, if it still is. */
    public boolean expire(Long id, LocalDateTime cutoff, String reason) {
        Optional<Order> found = orderRepository.findById(id);
        if (found.isEmpty() || !found.get().isOpen() || !found.get().getUpdatedAt().isBefore(cutoff)) {
            return false;
        }
        cancel(found.get(), reason, null);
        return true;
    }

    /** Moves an open order to CANCELLED and announces it. */
    void cancel(Order order, String reason, String causationId) {
        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);
        announceCancellation(order, reason, causationId);
        log.warn("Order {} cancelled: {}", order.getId(), reason);
    }

    /** Publishes {@code order.cancelled} (again, for a late saga event that shows a compensation was missed). */
    void announceCancellation(Order order, String reason, String causationId) {
        eventPublisher.publish(new OrderCancelledEvent(order.getId(), order.getCustomerId(), reason), null, causationId);
    }

    // ------------------------------------------------------------------ helpers

    private Pageable pageable(int pageNumber, int pageSize, String sortBy) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BusinessException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS, "INVALID_SORT_FIELD");
        }
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(pageNumber, 0), size, Sort.by(sortBy).ascending());
    }

    static List<LineItem> eventLines(Order order) {
        return order.getLines().stream()
                .map(l -> new LineItem(l.getProductId(), l.getQuantity(), l.getUnitPrice()))
                .toList();
    }

    OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .currency(order.getCurrency())
                .totalAmount(order.getTotalAmount())
                .items(order.getLines().stream().map(l -> OrderResponse.Line.builder()
                        .productId(l.getProductId())
                        .quantity(l.getQuantity())
                        .unitPrice(l.getUnitPrice())
                        .lineTotal(l.getLineTotal())
                        .build()).toList())
                .version(order.getVersion())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
