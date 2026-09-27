package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class ProductRepositoryTest {
    @Autowired
    private ProductRepository productRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("99.99"))
                .sku("SKU-TEST-001")
                .category("Electronics")
                .quantityAvailable(100)
                .build();
        productRepository.save(testProduct);
    }

    @Test
    void testFindBySku_Success() {
        Optional<Product> result = productRepository.findBySku("SKU-TEST-001");

        assertThat(result).isPresent();
        assertThat(result.get().getSku()).isEqualTo("SKU-TEST-001");
    }

    @Test
    void testFindBySku_NotFound() {
        Optional<Product> result = productRepository.findBySku("SKU-NOTFOUND");

        assertThat(result).isEmpty();
    }

    @Test
    void testFindByCategory_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> result = productRepository.findByCategory("Electronics", pageable);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getCategory()).isEqualTo("Electronics");
    }

    @Test
    void testFindByCategory_Empty() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> result = productRepository.findByCategory("NonExistentCategory", pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void testFindByCategory_List() {
        List<Product> result = productRepository.findByCategory("Electronics");

        assertThat(result).isNotEmpty();
        assertThat(result.stream().allMatch(p -> p.getCategory().equals("Electronics"))).isTrue();
    }

    @Test
    void testSearchByName_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> result = productRepository.searchByName("Test", pageable);

        assertThat(result.getContent()).isNotEmpty();
    }

    @Test
    void testSearchByName_NoResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> result = productRepository.searchByName("NonExistent", pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void testFindAvailableProducts_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> result = productRepository.findAvailableProducts(pageable);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().stream().allMatch(p -> p.getQuantityAvailable() > 0)).isTrue();
    }

    @Test
    void testFindLowStockProducts_Success() {
        Product lowStockProduct = Product.builder()
                .name("Low Stock Product")
                .price(new BigDecimal("29.99"))
                .sku("SKU-LOW-001")
                .category("Electronics")
                .quantityAvailable(5)
                .build();
        productRepository.save(lowStockProduct);

        List<Product> result = productRepository.findLowStockProducts();

        assertThat(result).isNotEmpty();
        assertThat(result.stream().allMatch(p -> p.getQuantityAvailable() <= 10)).isTrue();
    }

    @Test
    void testExistsBySku_True() {
        boolean result = productRepository.existsBySku("SKU-TEST-001");

        assertThat(result).isTrue();
    }

    @Test
    void testExistsBySku_False() {
        boolean result = productRepository.existsBySku("SKU-NOTFOUND");

        assertThat(result).isFalse();
    }

    @Test
    void testSaveAndRetrieve() {
        Product newProduct = Product.builder()
                .name("New Product")
                .description("New Description")
                .price(new BigDecimal("49.99"))
                .sku("SKU-NEW-001")
                .category("Books")
                .quantityAvailable(25)
                .build();

        Product saved = productRepository.save(newProduct);

        assertThat(saved.getId()).isNotNull();
        Optional<Product> retrieved = productRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getSku()).isEqualTo("SKU-NEW-001");
    }

    @Test
    void testUpdate() {
        testProduct.setPrice(new BigDecimal("149.99"));
        Product updated = productRepository.save(testProduct);

        Optional<Product> retrieved = productRepository.findById(updated.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPrice()).isEqualTo(new BigDecimal("149.99"));
    }

    @Test
    void testDelete() {
        Product toDelete = testProduct;
        productRepository.delete(toDelete);

        Optional<Product> result = productRepository.findById(toDelete.getId());
        assertThat(result).isEmpty();
    }
}
