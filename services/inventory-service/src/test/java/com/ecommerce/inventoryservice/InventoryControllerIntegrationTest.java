package com.ecommerce.inventoryservice;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
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
@WithMockUser(roles = "ADMIN")
@DisplayName("Inventory API (full stack, PostgreSQL)")
class InventoryControllerIntegrationTest {

    private static final String INVENTORY = "/api/v1/inventory";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InventoryRepository inventory;
    @Autowired
    private InventoryReservationRepository reservations;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        inventory.deleteAll();
    }

    private long create(String sku, int quantity) throws Exception {
        String body = mockMvc.perform(post(INVENTORY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + sku + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("an item can be created with zero stock")
    void createWithZero() throws Exception {
        mockMvc.perform(post(INVENTORY).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":\"SKU-Z\",\"quantity\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    @DisplayName("a duplicate product is a 409 problem; a negative quantity is a 400 problem")
    void createErrors() throws Exception {
        create("SKU-1", 5);

        mockMvc.perform(post(INVENTORY).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":\"SKU-1\",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVENTORY_ALREADY_EXISTS"));
        mockMvc.perform(post(INVENTORY).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":\"SKU-2\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("reserve and release move stock; reserving more than is left is a 409 and changes nothing")
    void reserveAndRelease() throws Exception {
        long id = create("SKU-1", 10);

        mockMvc.perform(post(INVENTORY + "/" + id + "/reserve").param("quantity", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(6)).andExpect(header().string("ETag", "\"1\""));
        mockMvc.perform(post(INVENTORY + "/" + id + "/reserve").param("quantity", "7"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(post(INVENTORY + "/" + id + "/reserve").param("quantity", "6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
        mockMvc.perform(post(INVENTORY + "/" + id + "/release").param("quantity", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    @DisplayName("a stock-take may set the level to zero; a negative level is a 400")
    void stockTake() throws Exception {
        long id = create("SKU-1", 10);

        mockMvc.perform(put(INVENTORY + "/" + id).param("quantity", "0")).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
        mockMvc.perform(put(INVENTORY + "/" + id).param("quantity", "-3")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("If-Match protects against a lost update: a stale version is a 412, the current one is accepted")
    void ifMatch() throws Exception {
        long id = create("SKU-1", 10);

        mockMvc.perform(put(INVENTORY + "/" + id).param("quantity", "20").header("If-Match", "\"0\"")).andExpect(status().isOk());
        mockMvc.perform(put(INVENTORY + "/" + id).param("quantity", "30").header("If-Match", "\"0\""))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(get(INVENTORY + "/" + id)).andExpect(jsonPath("$.quantity").value(20)).andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    @DisplayName("a missing item is a 404 problem; listing is paged and sorts only by known fields")
    void readingAndErrors() throws Exception {
        create("SKU-1", 1);
        create("SKU-2", 2);

        mockMvc.perform(get(INVENTORY + "/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(INVENTORY).param("sortBy", "quantity")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get(INVENTORY).param("sortBy", "password")).andExpect(status().isBadRequest());
    }
}
