package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductDTO testProductDTO;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;

    @BeforeEach
    void setUp() {
        testProductDTO = ProductDTO.builder()
                .id(1L)
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("99.99"))
                .sku("SKU-TEST-001")
                .category("Electronics")
                .quantityAvailable(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = CreateProductRequest.builder()
                .name("New Product")
                .description("New Description")
                .price(new BigDecimal("49.99"))
                .sku("SKU-NEW-001")
                .category("Electronics")
                .quantityAvailable(50)
                .build();

        updateRequest = UpdateProductRequest.builder()
                .name("Updated Product")
                .price(new BigDecimal("59.99"))
                .build();
    }

    @Test
    void testCreateProduct_Success() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(testProductDTO);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(testProductDTO.getId().intValue())))
                .andExpect(jsonPath("$.name", is(testProductDTO.getName())))
                .andExpect(jsonPath("$.sku", is(testProductDTO.getSku())));

        verify(productService).createProduct(any(CreateProductRequest.class));
    }

    @Test
    void testCreateProduct_InvalidRequest() throws Exception {
        CreateProductRequest invalidRequest = CreateProductRequest.builder()
                .name("")  // Empty name
                .price(new BigDecimal("49.99"))
                .sku("SKU-NEW-001")
                .category("Electronics")
                .quantityAvailable(50)
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetProduct_Success() throws Exception {
        when(productService.getProductById(1L)).thenReturn(testProductDTO);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testProductDTO.getId().intValue())))
                .andExpect(jsonPath("$.name", is(testProductDTO.getName())));

        verify(productService).getProductById(1L);
    }

    @Test
    void testGetProduct_NotFound() throws Exception {
        when(productService.getProductById(999L))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());

        verify(productService).getProductById(999L);
    }

    @Test
    void testGetProductBySku_Success() throws Exception {
        when(productService.getProductBySku("SKU-TEST-001")).thenReturn(testProductDTO);

        mockMvc.perform(get("/api/products/sku/SKU-TEST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku", is("SKU-TEST-001")));

        verify(productService).getProductBySku("SKU-TEST-001");
    }

    @Test
    void testGetAllProducts_Success() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(testProductDTO), PageRequest.of(0, 10), 1);
        when(productService.getAllProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));

        verify(productService).getAllProducts(any());
    }

    @Test
    void testGetProductsByCategory_Success() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(testProductDTO), PageRequest.of(0, 10), 1);
        when(productService.getProductsByCategory("Electronics", PageRequest.of(0, 20))).thenReturn(page);

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(productService).getProductsByCategory(eq("Electronics"), any());
    }

    @Test
    void testUpdateProduct_Success() throws Exception {
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class)))
                .thenReturn(testProductDTO);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testProductDTO.getId().intValue())));

        verify(productService).updateProduct(eq(1L), any(UpdateProductRequest.class));
    }

    @Test
    void testDeleteProduct_Success() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }

    @Test
    void testReserveInventory_Success() throws Exception {
        doNothing().when(productService).reserveInventory(1L, 10);

        mockMvc.perform(post("/api/products/1/reserve")
                        .param("quantity", "10"))
                .andExpect(status().isOk());

        verify(productService).reserveInventory(1L, 10);
    }

    @Test
    void testReleaseInventory_Success() throws Exception {
        doNothing().when(productService).releaseInventory(1L, 10);

        mockMvc.perform(post("/api/products/1/release")
                        .param("quantity", "10"))
                .andExpect(status().isOk());

        verify(productService).releaseInventory(1L, 10);
    }

    @Test
    void testGetAvailableProducts_Success() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(testProductDTO), PageRequest.of(0, 10), 1);
        when(productService.getAvailableProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(productService).getAvailableProducts(any());
    }

    @Test
    void testGetLowStockProducts_Success() throws Exception {
        when(productService.getLowStockProducts()).thenReturn(List.of(testProductDTO));

        mockMvc.perform(get("/api/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(productService).getLowStockProducts();
    }
}
