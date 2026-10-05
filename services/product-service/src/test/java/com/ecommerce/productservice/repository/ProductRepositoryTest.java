package com.ecommerce.productservice.repository;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.productservice.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@PostgresIntegrationTest
@DisplayName("ProductRepository (PostgreSQL)")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    private Product save(String sku, String name, String category, String price) {
        return repository.saveAndFlush(Product.builder().name(name).price(new BigDecimal(price)).sku(sku).category(category).build());
    }

    @Test
    @DisplayName("a product is stored with its currency and version, and has no stock column")
    void roundTrip() {
        Product saved = save("SKU-001", "Headphones", "Electronics", "79.99");
        entityManager.clear();

        Product found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.getVersion()).isZero();
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'products'", String.class))
                .doesNotContain("quantity_available");
    }

    @Test
    @DisplayName("the SKU is unique, enforced by the database")
    void uniqueSku() {
        save("SKU-001", "Headphones", "Electronics", "79.99");

        assertThatThrownBy(() -> save("SKU-001", "Other", "Electronics", "1.00")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("products are found by SKU, several SKUs at once, category and name")
    void queries() {
        save("SKU-001", "Wireless Headphones", "Electronics", "79.99");
        save("SKU-002", "USB-C Cable", "Electronics", "12.99");
        save("SKU-003", "Cotton T-Shirt", "Clothing", "19.99");

        assertThat(repository.findBySku("SKU-002")).isPresent();
        assertThat(repository.findBySkuIn(List.of("SKU-001", "SKU-003", "NOPE"))).extracting(Product::getSku).containsExactlyInAnyOrder("SKU-001", "SKU-003");
        assertThat(repository.findByCategory("Electronics", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(repository.searchByName("headphone", PageRequest.of(0, 10)).getContent()).extracting(Product::getSku).containsExactly("SKU-001");
        assertThat(repository.existsBySku("SKU-003")).isTrue();
        assertThat(repository.existsBySku("NOPE")).isFalse();
    }
}
