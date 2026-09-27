package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
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
    void testCreateProduct_Success() {
        when(productRepository.existsBySku(createRequest.getSku())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductDTO result = productService.createProduct(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(testProduct.getName());
        assertThat(result.getSku()).isEqualTo(testProduct.getSku());
        verify(productRepository).existsBySku(createRequest.getSku());
        verify(productRepository).save(any(Product.class));
        verify(eventPublisher).publishProductCreatedEvent(any());
    }

    @Test
    void testCreateProduct_DuplicateSku() {
        when(productRepository.existsBySku(createRequest.getSku())).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(createRequest))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("already exists");

        verify(productRepository).existsBySku(createRequest.getSku());
        verify(productRepository, never()).save(any());
    }

    @Test
    void testGetProductById_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        ProductDTO result = productService.getProductById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Product");
        verify(productRepository).findById(1L);
    }

    @Test
    void testGetProductById_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).findById(999L);
    }

    @Test
    void testGetProductBySku_Success() {
        when(productRepository.findBySku("SKU-TEST-001")).thenReturn(Optional.of(testProduct));

        ProductDTO result = productService.getProductBySku("SKU-TEST-001");

        assertThat(result).isNotNull();
        assertThat(result.getSku()).isEqualTo("SKU-TEST-001");
        verify(productRepository).findBySku("SKU-TEST-001");
    }

    @Test
    void testGetAllProducts_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);

        when(productRepository.findAll(pageable)).thenReturn(productPage);

        Page<ProductDTO> result = productService.getAllProducts(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(productRepository).findAll(pageable);
    }

    @Test
    void testGetProductsByCategory_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);

        when(productRepository.findByCategory("Electronics", pageable)).thenReturn(productPage);

        Page<ProductDTO> result = productService.getProductsByCategory("Electronics", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findByCategory("Electronics", pageable);
    }

    @Test
    void testUpdateProduct_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductDTO result = productService.updateProduct(1L, updateRequest);

        assertThat(result).isNotNull();
        verify(productRepository).findById(1L);
        verify(productRepository).save(any(Product.class));
        verify(eventPublisher).publishProductUpdatedEvent(any());
    }

    @Test
    void testUpdateProduct_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(999L, updateRequest))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).findById(999L);
        verify(productRepository, never()).save(any());
    }

    @Test
    void testDeleteProduct_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        productService.deleteProduct(1L);

        verify(productRepository).findById(1L);
        verify(productRepository).deleteById(1L);
        verify(eventPublisher).publishProductDeletedEvent(any());
    }

    @Test
    void testDeleteProduct_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository).findById(999L);
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void testReserveInventory_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        productService.reserveInventory(1L, 10);

        verify(productRepository).findById(1L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void testReserveInventory_InsufficientStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> productService.reserveInventory(1L, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient inventory");

        verify(productRepository).findById(1L);
        verify(productRepository, never()).save(any());
    }

    @Test
    void testReleaseInventory_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        productService.releaseInventory(1L, 10);

        verify(productRepository).findById(1L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void testGetLowStockProducts_Success() {
        Product lowStockProduct = testProduct.toBuilder().quantityAvailable(5).build();
        when(productRepository.findLowStockProducts()).thenReturn(List.of(lowStockProduct));

        List<ProductDTO> result = productService.getLowStockProducts();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(productRepository).findLowStockProducts();
    }

    @Test
    void testGetAvailableProducts_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Product> products = List.of(testProduct);
        Page<Product> productPage = new PageImpl<>(products, pageable, 1);

        when(productRepository.findAvailableProducts(pageable)).thenReturn(productPage);

        Page<ProductDTO> result = productService.getAvailableProducts(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findAvailableProducts(pageable);
    }
}
