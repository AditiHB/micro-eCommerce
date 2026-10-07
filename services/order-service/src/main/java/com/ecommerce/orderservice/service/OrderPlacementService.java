package com.ecommerce.orderservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.DependencyUnavailableException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.orderservice.client.CatalogClient;
import com.ecommerce.orderservice.client.CustomerDirectoryClient;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Turns an order request into a placed, priced order. It validates the customer and the products and takes
 * each unit price from the catalogue - a client never states a price - <em>before</em> any database
 * transaction opens, then hands the priced lines to {@link OrderService#place} for the single atomic write.
 *
 * <p>Retrying is made safe by the {@code Idempotency-Key}: a repeated request with the same key and body
 * returns the order already created instead of creating another. That - not a blind retry around the write - is
 * the protection against a response lost to a timeout.
 */
@Service
@Slf4j
public class OrderPlacementService {

    private final OrderService orderService;
    private final CatalogClient catalog;
    private final CustomerDirectoryClient customers;
    private final OrderProperties properties;
    private final Executor remoteCallExecutor;

    public OrderPlacementService(OrderService orderService, CatalogClient catalog, CustomerDirectoryClient customers,
                                 OrderProperties properties, @Qualifier("remoteCallExecutor") Executor remoteCallExecutor) {
        this.orderService = orderService;
        this.catalog = catalog;
        this.customers = customers;
        this.properties = properties;
        this.remoteCallExecutor = remoteCallExecutor;
    }

    public OrderService.Placement placeOrder(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null && properties.isIdempotencyKeyRequired()) {
            throw new BusinessException("The Idempotency-Key header is required to create an order", "IDEMPOTENCY_KEY_REQUIRED");
        }
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 200)) {
            throw new BusinessException("Idempotency-Key must be 1-200 characters", "IDEMPOTENCY_KEY_INVALID");
        }

        String principal = principal();
        List<CreateOrderRequest.Item> items = mergeDuplicateProducts(request.normalizedItems());
        String requestHash = fingerprint(request.getCustomerId(), items);

        var replay = orderService.findReplay(principal, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            log.info("Idempotency-Key replay for {}: returning order {}", principal, replay.get().order().getId());
            return replay.get();
        }

        // The customer check and the catalogue lookup are independent reads, so they go out concurrently on the
        // dedicated remoteCallExecutor instead of one after another. SecurityContextHolder is thread-local, so the
        // caller's token (which both clients relay downstream) is captured here and set explicitly on each
        // executor thread - without that, the relayed Authorization header would silently disappear.
        SecurityContext callerContext = SecurityContextHolder.getContext();
        CompletableFuture<Boolean> customerExists = submitRemote(
                () -> onContext(callerContext, () -> remote("customer service", () -> customers.exists(request.getCustomerId()))));
        CompletableFuture<Map<String, CatalogClient.ProductPrice>> priceLookup = submitRemote(
                () -> onContext(callerContext, () -> remote("product catalogue",
                        () -> catalog.lookup(items.stream().map(CreateOrderRequest.Item::getProductId).toList()))));

        if (!join(customerExists)) {
            throw new UnprocessableEntityException("Customer " + request.getCustomerId() + " does not exist", "CUSTOMER_NOT_FOUND");
        }

        Map<String, CatalogClient.ProductPrice> prices = join(priceLookup);
        Set<String> unknown = new TreeSet<>();
        items.forEach(i -> {
            if (!prices.containsKey(i.getProductId())) {
                unknown.add(i.getProductId());
            }
        });
        if (!unknown.isEmpty()) {
            throw new UnprocessableEntityException("Unknown product(s): " + unknown, "PRODUCT_NOT_FOUND");
        }
        Set<String> currencies = new TreeSet<>();
        items.forEach(i -> currencies.add(prices.get(i.getProductId()).currency()));
        if (currencies.size() != 1) {
            throw new UnprocessableEntityException("An order must be in a single currency, but the products are priced in " + currencies,
                    "MIXED_CURRENCIES");
        }

        List<OrderService.PricedLine> lines = new ArrayList<>(items.size());
        for (CreateOrderRequest.Item item : items) {
            BigDecimal unitPrice = prices.get(item.getProductId()).price();
            lines.add(new OrderService.PricedLine(item.getProductId(), item.getQuantity(), unitPrice));
        }

        try {
            return orderService.place(request.getCustomerId(), currencies.iterator().next(), lines, principal, idempotencyKey, requestHash);
        } catch (DataIntegrityViolationException raced) {
            // Another request with the same key committed between our check and our insert: that one won.
            return orderService.findReplay(principal, idempotencyKey, requestHash).orElseThrow(() -> raced);
        }
    }

    /** Calls another service, turning "down, too slow, or breaker open" into one clear 503. */
    private <T> T remote(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException | CallNotPermittedException e) {
            log.warn("Cannot reach the {}: {}", what, e.getMessage());
            throw new DependencyUnavailableException("The " + what + " is unavailable right now. Please retry shortly.");
        }
    }

    /**
     * Dispatches to {@link #remoteCallExecutor}, turning pool/queue saturation into the same
     * {@link DependencyUnavailableException} every other failure of a remote call produces. Without this,
     * {@code ThreadPoolTaskExecutor}'s default rejection policy throws {@link RejectedExecutionException}
     * synchronously out of {@code supplyAsync} itself - bypassing {@link #remote} entirely and surfacing as an
     * unhandled 500 instead of a clean 503.
     */
    private <T> CompletableFuture<T> submitRemote(Supplier<T> call) {
        try {
            return CompletableFuture.supplyAsync(call, remoteCallExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("remoteCallExecutor is saturated; rejecting this order's remote-call fan-out");
            return CompletableFuture.failedFuture(
                    new DependencyUnavailableException("The system is busy right now. Please retry shortly."));
        }
    }

    /** Runs {@code call} on the executor thread with the calling thread's security context installed. */
    private static <T> T onContext(SecurityContext context, Supplier<T> call) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(context);
        try {
            return call.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /** Unwraps the future back to the original exception {@link #remote} threw, instead of a {@link CompletionException}. */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /** Two lines for the same product become one, so a product is priced and reserved once per order. */
    private static List<CreateOrderRequest.Item> mergeDuplicateProducts(List<CreateOrderRequest.Item> items) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (CreateOrderRequest.Item item : items) {
            merged.merge(item.getProductId().trim(), item.getQuantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> new CreateOrderRequest.Item(e.getKey(), e.getValue()))
                .toList();
    }

    private static String principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static String fingerprint(Long customerId, List<CreateOrderRequest.Item> items) {
        StringBuilder canonical = new StringBuilder("customer=").append(customerId);
        items.forEach(i -> canonical.append(";").append(i.getProductId()).append("x").append(i.getQuantity()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
