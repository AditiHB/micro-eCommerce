package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.events.LineItem;
import com.ecommerce.common.exception.NonRetryableEventException;
import com.ecommerce.inventoryservice.Inventory;
import com.ecommerce.inventoryservice.InventoryRepository;
import com.ecommerce.inventoryservice.InventoryReservation;
import com.ecommerce.inventoryservice.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Holds and returns stock for whole orders. Always runs inside the caller's transaction (the saga handler's), so
 * the reservation, the inbox claim and the event announcing it commit together or not at all.
 *
 * <p><b>Reserve is all-or-nothing and never oversells.</b> The order's products are locked in a fixed order (no
 * deadlocks between orders sharing products), every line is checked first, and only if all of them can be
 * satisfied is stock taken - each decrement a single conditional statement backed by the database's
 * {@code CHECK (quantity >= 0)}. A shortfall changes nothing and is reported as a result, not thrown.
 *
 * <p><b>Release gives back exactly what was held,</b> once: only the order's un-released reservation lines are
 * returned, and they are marked released, so a second cancellation is a no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    /** Outcome of a reservation attempt: success, or why it could not be satisfied. */
    public record Result(boolean reserved, String reason) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result failed(String reason) {
            return new Result(false, reason);
        }
    }

    public Result reserve(Long orderId, List<LineItem> lines) {
        Map<String, Integer> wanted = normalize(orderId, lines);

        if (reservationRepository.existsByOrderId(orderId)) {
            // Stock was already taken for this order (a replay of an event this service has handled).
            log.info("Order {} already has a reservation - not taking stock again", orderId);
            return Result.ok();
        }

        List<Inventory> locked = inventoryRepository.lockByProductIds(wanted.keySet());
        Map<String, Inventory> byProduct = new TreeMap<>();
        locked.forEach(i -> byProduct.put(i.getProductId(), i));

        for (Map.Entry<String, Integer> line : wanted.entrySet()) {
            Inventory stock = byProduct.get(line.getKey());
            if (stock == null) {
                return Result.failed("Unknown product " + line.getKey());
            }
            if (stock.getQuantity() < line.getValue()) {
                return Result.failed("Insufficient stock for " + line.getKey()
                        + " (requested " + line.getValue() + ", available " + stock.getQuantity() + ")");
            }
        }

        for (Map.Entry<String, Integer> line : wanted.entrySet()) {
            if (inventoryRepository.decrementIfAvailable(line.getKey(), line.getValue()) != 1) {
                // Cannot happen while the rows are locked; if it ever does, fail the whole transaction (retried).
                throw new IllegalStateException("Stock for " + line.getKey() + " changed under a lock");
            }
        }
        wanted.forEach((productId, quantity) -> reservationRepository.save(InventoryReservation.builder()
                .orderId(orderId).productId(productId).quantity(quantity).build()));
        log.info("Reserved stock for order {}: {}", orderId, wanted);
        return Result.ok();
    }

    /** Returns the stock held for the order; the lines that were released (empty if nothing was held). */
    public List<LineItem> release(Long orderId) {
        List<InventoryReservation> active = reservationRepository.lockActiveByOrderId(orderId);
        List<LineItem> released = new ArrayList<>(active.size());
        LocalDateTime now = LocalDateTime.now();
        for (InventoryReservation reservation : active) {
            if (inventoryRepository.increment(reservation.getProductId(), reservation.getQuantity()) == 0) {
                log.warn("Product {} no longer exists; its reserved stock for order {} cannot be returned",
                        reservation.getProductId(), orderId);
            }
            reservation.setReleasedAt(now);
            reservationRepository.save(reservation);
            released.add(new LineItem(reservation.getProductId(), reservation.getQuantity(), null));
        }
        if (released.isEmpty()) {
            log.info("Order {} holds no stock - nothing to release", orderId);
        } else {
            log.info("Released stock held for order {}: {}", orderId, released);
        }
        released.sort(Comparator.comparing(LineItem::getProductId));
        return released;
    }

    /** Merges duplicate products and rejects lines no retry could ever make valid. */
    private static Map<String, Integer> normalize(Long orderId, List<LineItem> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new NonRetryableEventException("Order " + orderId + " has no lines to reserve");
        }
        Map<String, Integer> wanted = new TreeMap<>();
        for (LineItem line : lines) {
            if (line.getProductId() == null || line.getProductId().isBlank() || line.getQuantity() <= 0) {
                throw new NonRetryableEventException("Order " + orderId + " has an invalid line: " + line.getProductId() + " x " + line.getQuantity());
            }
            wanted.merge(line.getProductId(), line.getQuantity(), Integer::sum);
        }
        return wanted;
    }
}
