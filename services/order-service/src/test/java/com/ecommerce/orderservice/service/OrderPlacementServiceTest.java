package com.ecommerce.orderservice.service;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.DependencyUnavailableException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.orderservice.client.CatalogClient;
import com.ecommerce.orderservice.client.CatalogClient.ProductPrice;
import com.ecommerce.orderservice.client.CustomerDirectoryClient;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacementService")
class OrderPlacementServiceTest {

    @Mock
    private OrderService orderService;
    @Mock
    private CatalogClient catalog;
    @Mock
    private CustomerDirectoryClient customers;

    private final OrderProperties properties = new OrderProperties();
    private OrderPlacementService placement;

    @BeforeEach
    void setUp() {
        // Synchronous executor: the concurrency itself is covered by the executor's own config/behavior, not by
        // these unit tests, which only need the customer check and the catalogue lookup to run (on whichever
        // thread) and their results combined correctly.
        placement = new OrderPlacementService(orderService, catalog, customers, properties, Runnable::run);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "n/a"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CreateOrderRequest request(CreateOrderRequest.Item... items) {
        return CreateOrderRequest.builder().customerId(7L).items(List.of(items)).build();
    }

    private static CreateOrderRequest.Item item(String sku, int quantity) {
        return new CreateOrderRequest.Item(sku, quantity);
    }

    private static OrderService.Placement placed(boolean replayed) {
        return new OrderService.Placement(OrderResponse.builder().id(1L).status(OrderStatus.PENDING).build(), replayed);
    }

    private void catalogKnows(String sku, String price, String currency) {
        when(catalog.lookup(anyList())).thenReturn(Map.of(sku, new ProductPrice(sku, "n", new BigDecimal(price), currency)));
    }

    @Test
    @DisplayName("prices come from the catalogue, never from the request")
    void pricesFromTheCatalogue() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        catalogKnows("SKU-001", "79.99", "USD");
        when(orderService.place(any(), any(), anyList(), any(), any(), any())).thenReturn(placed(false));

        placement.placeOrder(request(item("SKU-001", 2)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderService.PricedLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(orderService).place(eq(7L), eq("USD"), lines.capture(), eq("alice"), eq(null), any());
        assertThat(lines.getValue()).containsExactly(new OrderService.PricedLine("SKU-001", 2, new BigDecimal("79.99")));
    }

    @Test
    @DisplayName("two lines for the same product become one, quantities added")
    void mergesDuplicateProducts() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        catalogKnows("SKU-001", "10.00", "USD");
        when(orderService.place(any(), any(), anyList(), any(), any(), any())).thenReturn(placed(false));

        placement.placeOrder(request(item("SKU-001", 2), item("SKU-001", 3)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderService.PricedLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(orderService).place(any(), any(), lines.capture(), any(), any(), any());
        assertThat(lines.getValue()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(5));
    }

    @Test
    @DisplayName("an unknown customer is a 422 and nothing is written")
    void unknownCustomer() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(false);
        // The catalogue lookup and the customer check now run concurrently, so the catalogue is still called even
        // though the order is about to be rejected - only the write is skipped.
        catalogKnows("SKU-001", "10.00", "USD");

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting(e -> ((UnprocessableEntityException) e).getErrorCode()).isEqualTo("CUSTOMER_NOT_FOUND");
        verify(orderService, never()).place(any(), any(), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown product is a 422 naming it")
    void unknownProduct() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        when(catalog.lookup(anyList())).thenReturn(Map.of());

        assertThatThrownBy(() -> placement.placeOrder(request(item("NOPE-1", 1)), null))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("NOPE-1");
    }

    @Test
    @DisplayName("products priced in different currencies cannot share an order")
    void mixedCurrencies() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        when(catalog.lookup(anyList())).thenReturn(Map.of(
                "SKU-001", new ProductPrice("SKU-001", "a", BigDecimal.ONE, "USD"),
                "SKU-002", new ProductPrice("SKU-002", "b", BigDecimal.ONE, "EUR")));

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1), item("SKU-002", 1)), null))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting(e -> ((UnprocessableEntityException) e).getErrorCode()).isEqualTo("MIXED_CURRENCIES");
    }

    @Test
    @DisplayName("a customer service that is down or too slow becomes a 503, not a stack trace")
    void customerServiceDown() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenThrow(new ResourceAccessException("read timed out"));

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null))
                .isInstanceOf(DependencyUnavailableException.class)
                .satisfies(e -> assertThat(((DependencyUnavailableException) e).getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("a catalogue returning 5xx, or an open circuit breaker, is also a 503")
    void catalogueDown() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        when(catalog.lookup(anyList())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("catalog")));

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null)).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("a saturated remote-call executor is also a 503, not a raw RejectedExecutionException")
    void executorSaturated() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        java.util.concurrent.Executor rejecting = task -> {
            throw new java.util.concurrent.RejectedExecutionException("pool saturated");
        };
        OrderPlacementService saturated = new OrderPlacementService(orderService, catalog, customers, properties, rejecting);

        assertThatThrownBy(() -> saturated.placeOrder(request(item("SKU-001", 1)), null))
                .isInstanceOf(DependencyUnavailableException.class)
                .satisfies(e -> assertThat(((DependencyUnavailableException) e).getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(catalog, customers);
    }

    @Test
    @DisplayName("a repeated Idempotency-Key returns the original order without calling anything else")
    void replayShortCircuits() {
        when(orderService.findReplay(eq("alice"), eq("key-1"), any())).thenReturn(Optional.of(placed(true)));

        OrderService.Placement result = placement.placeOrder(request(item("SKU-001", 1)), "key-1");

        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(catalog, customers);
        verify(orderService, never()).place(any(), any(), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("losing the race to a concurrent request with the same key returns the winner's order")
    void lostRaceReplays() {
        when(orderService.findReplay(any(), eq("key-1"), any())).thenReturn(Optional.empty(), Optional.of(placed(true)));
        when(customers.exists(7L)).thenReturn(true);
        catalogKnows("SKU-001", "10.00", "USD");
        when(orderService.place(any(), any(), anyList(), any(), eq("key-1"), any()))
                .thenThrow(new DataIntegrityViolationException("uq_idempotency_principal_key"));

        OrderService.Placement result = placement.placeOrder(request(item("SKU-001", 1)), "key-1");

        assertThat(result.replayed()).isTrue();
    }

    @Test
    @DisplayName("a data-integrity failure without an idempotency key is a real error and is not hidden")
    void realIntegrityErrorPropagates() {
        when(orderService.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.exists(7L)).thenReturn(true);
        catalogKnows("SKU-001", "10.00", "USD");
        when(orderService.place(any(), any(), anyList(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("when idempotency keys are required, an order without one is refused")
    void keyRequired() {
        properties.setIdempotencyKeyRequired(true);

        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("an absurd Idempotency-Key is rejected")
    void keyValidated() {
        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), "x".repeat(201)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> placement.placeOrder(request(item("SKU-001", 1)), "  "))
                .isInstanceOf(BusinessException.class);
    }
}
