package com.ecommerce.orderservice;

import com.ecommerce.common.eventsourcing.EventStoreRepository;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.orderservice.client.CatalogClient;
import com.ecommerce.orderservice.client.CatalogClient.ProductPrice;
import com.ecommerce.orderservice.client.CustomerDirectoryClient;
import com.ecommerce.orderservice.idempotency.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@PostgresIntegrationTest
// Filters are off in this test, so the caller is supplied directly. ADMIN has cross-customer access;
// per-customer ownership rules are covered by OrderOwnershipTest.
@WithMockUser(roles = "ADMIN")
@DisplayName("Order API (full stack, PostgreSQL)")
class OrderControllerIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private EventStoreRepository eventStore;
    @Autowired
    private IdempotencyRecordRepository idempotency;

    @MockBean
    private CatalogClient catalog;
    @MockBean
    private CustomerDirectoryClient customers;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        eventStore.deleteAll();
        idempotency.deleteAll();
        orders.deleteAll();
        when(customers.exists(1L)).thenReturn(true);
        when(catalog.lookup(anyList())).thenReturn(Map.of(
                "SKU-001", new ProductPrice("SKU-001", "Headphones", new BigDecimal("79.99"), "USD"),
                "SKU-002", new ProductPrice("SKU-002", "Cable", new BigDecimal("12.99"), "USD")));
    }

    private static String body(String items) {
        return "{\"customerId\":1,\"items\":" + items + "}";
    }

    private long createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"productId\":\"SKU-001\",\"quantity\":1}]")))
                .andExpect(status().isCreated()).andReturn();
        return Long.parseLong(result.getResponse().getHeader("Location").substring(ORDERS.length() + 1));
    }

    @Test
    @DisplayName("creates a priced order: 201, Location, ETag, and the total computed by the server")
    void createsAPricedOrder() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"productId\":\"SKU-001\",\"quantity\":2},{\"productId\":\"SKU-002\",\"quantity\":1}]")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(ORDERS + "/")))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalAmount").value(172.97))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(79.99))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("the original single productId + quantity request shape still works")
    void legacyShape() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"productId\":\"SKU-002\",\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(38.97))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("a client-supplied price or total is rejected, not trusted")
    void clientCannotSetPrices() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"items\":[{\"productId\":\"SKU-001\",\"quantity\":1,\"unitPrice\":0.01}],\"totalAmount\":0.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("an Idempotency-Key makes a retry return the same order, flagged as a replay")
    void idempotentCreate() throws Exception {
        String request = body("[{\"productId\":\"SKU-001\",\"quantity\":1}]");
        MvcResult first = mockMvc.perform(post(ORDERS).header("Idempotency-Key", "abc-123").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed")).andReturn();
        MvcResult second = mockMvc.perform(post(ORDERS).header("Idempotency-Key", "abc-123").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true")).andReturn();

        assertThat(second.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("reusing a key for a different request is a 422 problem")
    void keyReuse() throws Exception {
        mockMvc.perform(post(ORDERS).header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                .content(body("[{\"productId\":\"SKU-001\",\"quantity\":1}]"))).andExpect(status().isCreated());

        mockMvc.perform(post(ORDERS).header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"productId\":\"SKU-001\",\"quantity\":9}]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("an unknown product is a 422, and an unknown customer too - nothing is created")
    void unknownReferences() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":\"NOPE\",\"quantity\":1}]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString("NOPE")));
        when(customers.exists(1L)).thenReturn(false);
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":\"SKU-001\",\"quantity\":1}]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
        assertThat(orders.count()).isZero();
    }

    @Test
    @DisplayName("validation errors are a 400 problem listing each offending field")
    void validation() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"SKU-001\",\"quantity\":-2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists());
    }

    @Test
    @DisplayName("an unreadable body is a 400 problem")
    void malformedBody() throws Exception {
        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("an order is read back by id with its ETag; an unknown id is a 404 problem")
    void readById() throws Exception {
        long id = createOrder();

        mockMvc.perform(get(ORDERS + "/" + id))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get(ORDERS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(ORDERS + "/999999"));
    }

    @Test
    @DisplayName("listing is paged; sorting by an unknown field is a 400")
    void listing() throws Exception {
        createOrder();
        createOrder();

        mockMvc.perform(get(ORDERS).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(ORDERS).param("sortBy", "customer.email"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_SORT_FIELD"));
    }

    @Test
    @DisplayName("cancelling returns the cancelled order; cancelling again is a harmless 200")
    void cancel() throws Exception {
        long id = createOrder();

        mockMvc.perform(post(ORDERS + "/" + id + "/cancel").param("reason", "changed my mind"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post(ORDERS + "/" + id + "/cancel")).andExpect(status().isOk());

        assertThat(outbox.findAll()).extracting(o -> o.getEventType()).containsExactly("order.created", "order.cancelled");
    }

    @Test
    @DisplayName("cancelling with a stale If-Match is a 412 and changes nothing")
    void cancelWithStaleEtag() throws Exception {
        long id = createOrder();

        mockMvc.perform(post(ORDERS + "/" + id + "/cancel").header("If-Match", "\"41\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_FAILED"));
        mockMvc.perform(post(ORDERS + "/" + id + "/cancel").header("If-Match", "\"0\"")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("back office cannot set COMPLETED by hand (409), but can cancel through the status endpoint")
    void manualStatus() throws Exception {
        long id = createOrder();

        mockMvc.perform(put(ORDERS + "/" + id + "/status").param("status", "COMPLETED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ORDER_STATUS_MANAGED_BY_SAGA"));
        mockMvc.perform(put(ORDERS + "/" + id + "/status").param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(put(ORDERS + "/" + id + "/status").param("status", "NONSENSE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a catalogue outage is a 503 problem, not a 500")
    void catalogueOutage() throws Exception {
        when(catalog.lookup(anyList())).thenThrow(new org.springframework.web.client.ResourceAccessException("timed out"));

        mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body("[{\"productId\":\"SKU-001\",\"quantity\":1}]")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("DEPENDENCY_UNAVAILABLE"));
        assertThat(orders.count()).isZero();
    }
}
