package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.exception.GlobalExceptionHandler;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("Product API (controller slice)")
class ProductControllerTest {

    private static final String PRODUCTS = "/api/v1/products";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ProductService productService;

    private ProductDTO product;

    @BeforeEach
    void setUp() {
        product = ProductDTO.builder().id(1L).name("Headphones").description("Wireless").price(new BigDecimal("79.99"))
                .currency("USD").sku("SKU-001").category("Electronics").version(3L)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("creating a product returns 201, Location and the version as ETag; the body has no stock field")
    void create() throws Exception {
        when(productService.createProduct(any())).thenReturn(product);

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Headphones\",\"price\":79.99,\"sku\":\"SKU-001\",\"category\":\"Electronics\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(PRODUCTS + "/")))
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.quantityAvailable").doesNotExist());
    }

    @Test
    @DisplayName("an invalid product is a 400 problem naming the fields")
    void validation() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":0,\"sku\":\"\",\"category\":\"\",\"currency\":\"dollars\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("a duplicate SKU is a 409 problem")
    void duplicateSku() throws Exception {
        when(productService.createProduct(any())).thenThrow(new DuplicateSkuException("SKU-001"));

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"price\":1,\"sku\":\"SKU-001\",\"category\":\"C\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("DUPLICATE_SKU"));
    }

    @Test
    @DisplayName("a product is read by id or SKU, with its ETag; an unknown one is a 404 problem")
    void read() throws Exception {
        when(productService.getProductById(1L)).thenReturn(product);
        when(productService.getProductBySku("SKU-001")).thenReturn(product);
        when(productService.getProductById(9L)).thenThrow(new ProductNotFoundException(9L));

        mockMvc.perform(get(PRODUCTS + "/1")).andExpect(status().isOk()).andExpect(header().string("ETag", "\"3\""));
        mockMvc.perform(get(PRODUCTS + "/sku/SKU-001")).andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("SKU-001"));
        mockMvc.perform(get(PRODUCTS + "/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("the batch lookup prices several SKUs in one call")
    void lookup() throws Exception {
        when(productService.lookupBySkus(anyCollection())).thenReturn(List.of(product));

        mockMvc.perform(get(PRODUCTS + "/lookup").param("skus", "SKU-001, SKU-404"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].price").value(79.99));
    }

    @Test
    @DisplayName("a lookup needs between 1 and 50 SKUs")
    void lookupBounds() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/lookup").param("skus", " , ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LOOKUP"));
        String tooMany = String.join(",", java.util.stream.IntStream.range(0, 51).mapToObj(i -> "SKU-" + i).toList());
        mockMvc.perform(get(PRODUCTS + "/lookup").param("skus", tooMany)).andExpect(status().isBadRequest());
        verify(productService, never()).lookupBySkus(any(Collection.class));
    }

    @Test
    @DisplayName("listing is paged")
    void list() throws Exception {
        when(productService.getAllProducts(any())).thenReturn(new PageImpl<>(List.of(product)));

        mockMvc.perform(get(PRODUCTS)).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("PUT passes If-Match to the service")
    void update() throws Exception {
        when(productService.updateProduct(eq(1L), any(), eq("\"3\""))).thenReturn(product);

        mockMvc.perform(put(PRODUCTS + "/1").header("If-Match", "\"3\"").contentType(MediaType.APPLICATION_JSON).content("{\"price\":89.99}"))
                .andExpect(status().isOk());
        verify(productService).updateProduct(eq(1L), any(), eq("\"3\""));
    }

    @Test
    @DisplayName("the stock operations are gone: the catalogue no longer owns stock")
    void noStockEndpoints() throws Exception {
        mockMvc.perform(post(PRODUCTS + "/1/reserve").param("quantity", "1")).andExpect(status().isNotFound());
        mockMvc.perform(post(PRODUCTS + "/1/release").param("quantity", "1")).andExpect(status().isNotFound());
        mockMvc.perform(get(PRODUCTS + "/low-stock")).andExpect(status().is4xxClientError());
        mockMvc.perform(get(PRODUCTS + "/available")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("delete returns 204")
    void deleteProduct() throws Exception {
        mockMvc.perform(delete(PRODUCTS + "/1")).andExpect(status().isNoContent());
        verify(productService).deleteProduct(1L);
    }
}
