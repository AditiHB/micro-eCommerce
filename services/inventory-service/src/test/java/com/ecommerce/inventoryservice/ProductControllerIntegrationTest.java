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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue API (merged in from product-service), same full-stack-against-PostgreSQL style as
 * {@link InventoryControllerIntegrationTest} - see that class for why security filters are disabled here
 * rather than mocked per-test.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@PostgresIntegrationTest
@WithMockUser(roles = "ADMIN")
@DisplayName("Product catalogue API (full stack, PostgreSQL)")
class ProductControllerIntegrationTest {

    private static final String PRODUCTS = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository products;

    @BeforeEach
    void setUp() {
        products.deleteAll();
    }

    private String createJson(String sku) {
        return "{\"name\":\"Widget\",\"price\":9.99,\"sku\":\"" + sku + "\",\"category\":\"Electronics\"}";
    }

    private long create(String sku) throws Exception {
        String body = mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(createJson(sku)))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("creating a product defaults its currency to USD and returns Location + ETag")
    void create() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(createJson("SKU-201")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("a duplicate SKU is a 409")
    void duplicateSku() throws Exception {
        create("SKU-202");

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(createJson("SKU-202")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("read by id and by SKU both find the same product")
    void readByIdAndSku() throws Exception {
        long id = create("SKU-203");

        mockMvc.perform(get(PRODUCTS + "/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-203"));
        mockMvc.perform(get(PRODUCTS + "/sku/{sku}", "SKU-203")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("an unknown product is a 404")
    void notFound() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the batch lookup prices several SKUs, bounded to 1-50")
    void lookup() throws Exception {
        create("SKU-204");
        create("SKU-205");

        mockMvc.perform(get(PRODUCTS + "/lookup").param("skus", "SKU-204,SKU-205,NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(PRODUCTS + "/lookup").param("skus", "")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search and category browse both page the catalogue")
    void searchAndCategory() throws Exception {
        create("SKU-206");

        mockMvc.perform(get(PRODUCTS + "/search").param("term", "Widget")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get(PRODUCTS + "/category/{category}", "Electronics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("a stale If-Match on update is a 412; a correct one applies the change")
    void ifMatch() throws Exception {
        long id = create("SKU-207");

        mockMvc.perform(put(PRODUCTS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"99\"").content("{\"price\":5.00}"))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(put(PRODUCTS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"0\"").content("{\"price\":5.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(5.00));
    }

    @Test
    @DisplayName("deleting a product returns 204 and it is then gone")
    void delete_() throws Exception {
        long id = create("SKU-208");

        mockMvc.perform(delete(PRODUCTS + "/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get(PRODUCTS + "/{id}", id)).andExpect(status().isNotFound());
    }
}
