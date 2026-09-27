package com.ecommerce.productservice;

import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9092"
})
class ProductServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @Transactional
    void testCreateProduct_EndToEnd() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("Integration Test Product")
                .description("Testing integration flow")
                .price(new BigDecimal("79.99"))
                .sku("SKU-INT-001")
                .category("Electronics")
                .quantityAvailable(50)
                .build();

        String json = "{\"name\":\"Integration Test Product\",\"description\":\"Testing integration flow\",\"price\":79.99,\"sku\":\"SKU-INT-001\",\"category\":\"Electronics\",\"quantityAvailable\":50}";
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Integration Test Product")))
                .andExpect(jsonPath("$.sku", is("SKU-INT-001")));

        Optional<Product> saved = productRepository.findBySku("SKU-INT-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getName()).isEqualTo("Integration Test Product");
    }

    @Test
    @Transactional
    void testGetProduct_EndToEnd() throws Exception {
        Product product = Product.builder()
                .name("Get Test Product")
                .price(new BigDecimal("49.99"))
                .sku("SKU-GET-001")
                .category("Books")
                .quantityAvailable(30)
                .build();
        Product saved = productRepository.save(product);

        mockMvc.perform(get("/api/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Get Test Product")));
    }

    @Test
    @Transactional
    void testUpdateProduct_EndToEnd() throws Exception {
        Product product = Product.builder()
                .name("Update Test Product")
                .price(new BigDecimal("59.99"))
                .sku("SKU-UPD-001")
                .category("Clothing")
                .quantityAvailable(40)
                .build();
        Product saved = productRepository.save(product);

        String updateJson = "{\"name\":\"Updated Product\",\"price\":69.99}";
        mockMvc.perform(put("/api/products/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Product")))
                .andExpect(jsonPath("$.price", is(69.99)));
    }

    @Test
    @Transactional
    void testDeleteProduct_EndToEnd() throws Exception {
        Product product = Product.builder()
                .name("Delete Test Product")
                .price(new BigDecimal("39.99"))
                .sku("SKU-DEL-001")
                .category("Home")
                .quantityAvailable(20)
                .build();
        Product saved = productRepository.save(product);

        mockMvc.perform(delete("/api/products/" + saved.getId()))
                .andExpect(status().isNoContent());

        Optional<Product> deleted = productRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    @Transactional
    void testGetProductsByCategory_EndToEnd() throws Exception {
        Product product1 = Product.builder()
                .name("Category Test 1")
                .price(new BigDecimal("29.99"))
                .sku("SKU-CAT-001")
                .category("Electronics")
                .quantityAvailable(15)
                .build();
        Product product2 = Product.builder()
                .name("Category Test 2")
                .price(new BigDecimal("39.99"))
                .sku("SKU-CAT-002")
                .category("Electronics")
                .quantityAvailable(25)
                .build();
        productRepository.save(product1);
        productRepository.save(product2);

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Transactional
    void testReserveInventory_Successful() throws Exception {
        Product product = Product.builder()
                .name("Reserve Test")
                .price(new BigDecimal("89.99"))
                .sku("SKU-RES-001")
                .category("Electronics")
                .quantityAvailable(100)
                .build();
        Product saved = productRepository.save(product);

        mockMvc.perform(post("/api/products/" + saved.getId() + "/reserve")
                        .param("quantity", "25"))
                .andExpect(status().isOk());

        Product updated = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getQuantityAvailable()).isEqualTo(75);
    }

    @Test
    @Transactional
    void testReleaseInventory_Successful() throws Exception {
        Product product = Product.builder()
                .name("Release Test")
                .price(new BigDecimal("99.99"))
                .sku("SKU-REL-001")
                .category("Electronics")
                .quantityAvailable(75)
                .build();
        Product saved = productRepository.save(product);

        mockMvc.perform(post("/api/products/" + saved.getId() + "/release")
                        .param("quantity", "25"))
                .andExpect(status().isOk());

        Product updated = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getQuantityAvailable()).isEqualTo(100);
    }

    @Test
    @Transactional
    void testSearchProducts_EndToEnd() throws Exception {
        Product product = Product.builder()
                .name("Search Test Product")
                .price(new BigDecimal("44.99"))
                .sku("SKU-SEARCH-001")
                .category("Books")
                .quantityAvailable(20)
                .build();
        productRepository.save(product);

        mockMvc.perform(get("/api/products/search")
                        .param("term", "Search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Transactional
    void testGetAvailableProducts_EndToEnd() throws Exception {
        Product availableProduct = Product.builder()
                .name("Available Product")
                .price(new BigDecimal("34.99"))
                .sku("SKU-AVAIL-001")
                .category("Home")
                .quantityAvailable(10)
                .build();
        productRepository.save(availableProduct);

        mockMvc.perform(get("/api/products/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }
}
