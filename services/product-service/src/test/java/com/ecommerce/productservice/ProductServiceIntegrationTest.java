package com.ecommerce.productservice;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.eventsourcing.EventStoreRepository;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.common.testsupport.SharedRedis;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.ProductEventPublisher;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * The catalogue against a real PostgreSQL and Redis: catalogue events go through the outbox atomically with the
 * change, entries are cached per key and evicted by key, and stock is no longer part of a product.
 */
@SpringBootTest
@PostgresIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Product catalogue (PostgreSQL + Redis)")
class ProductServiceIntegrationTest {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", SharedRedis::host);
        registry.add("spring.data.redis.port", SharedRedis::port);
    }

    @Autowired
    private ProductService service;
    @Autowired
    private ProductRepository repository;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private EventStoreRepository eventStore;
    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ProductEventPublisher publisher;

    private Cache cache() {
        return cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
    }

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        eventStore.deleteAll();
        repository.deleteAllInBatch();
        cache().clear();
        reset(AopTestUtils.<ProductEventPublisher>getUltimateTargetObject(publisher));
    }

    @AfterEach
    void tearDown() {
        SharedRedis.resume();
    }

    private ProductDTO create(String sku, String price) {
        return service.createProduct(CreateProductRequest.builder().name("Product " + sku).price(new BigDecimal(price))
                .sku(sku).category("Electronics").build());
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().map(o -> o.getEventType()).toList();
    }

    @Test
    @DisplayName("creating a product announces product.created through the outbox, keyed by the product id")
    void createAnnounces() {
        ProductDTO created = create("SKU-001", "79.99");

        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getEventType()).isEqualTo("product.created");
            assertThat(row.getTopic()).isEqualTo("product-events");
            assertThat(row.getMessageKey()).isEqualTo(String.valueOf(created.getId()));
            assertThat(row.getPayload()).contains("\"sku\":\"SKU-001\"").contains("\"currency\":\"USD\"");
        });
    }

    @Test
    @DisplayName("the change and its event are one atomic write: if announcing fails the product is not created")
    void atomic() {
        doThrow(new IllegalStateException("outbox unavailable")).when(AopTestUtils.<ProductEventPublisher>getUltimateTargetObject(publisher)).created(any());

        assertThatThrownBy(() -> create("SKU-001", "79.99")).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("a duplicate SKU is refused and announces nothing")
    void duplicate() {
        create("SKU-001", "79.99");
        outbox.deleteAll();

        assertThatThrownBy(() -> create("SKU-001", "1.00")).isInstanceOf(DuplicateSkuException.class);

        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("a product is cached by id and by SKU; changing it evicts both entries, so the price is never stale")
    void cacheAndEviction() {
        ProductDTO created = create("SKU-001", "79.99");
        service.getProductById(created.getId());
        service.getProductBySku("SKU-001");
        assertThat(cache().get("id:" + created.getId())).isNotNull();
        assertThat(cache().get("sku:SKU-001")).isNotNull();

        service.updateProduct(created.getId(), UpdateProductRequest.builder().price(new BigDecimal("89.99")).build(), null);

        assertThat(cache().get("id:" + created.getId())).isNull();
        assertThat(cache().get("sku:SKU-001")).isNull();
        assertThat(service.getProductBySku("SKU-001").getPrice()).isEqualByComparingTo("89.99");
        assertThat(outboxTypes()).containsExactly("product.created", "product.updated");
    }

    @Test
    @DisplayName("deleting evicts both entries and announces product.deleted")
    void deleteEvicts() {
        ProductDTO created = create("SKU-001", "79.99");
        service.getProductById(created.getId());

        service.deleteProduct(created.getId());

        assertThat(cache().get("id:" + created.getId())).isNull();
        assertThat(outboxTypes()).containsExactly("product.created", "product.deleted");
    }

    @Test
    @DisplayName("the batch lookup prices several SKUs in one query; unknown ones are absent")
    void lookup() {
        create("SKU-001", "79.99");
        create("SKU-002", "12.99");

        assertThat(service.lookupBySkus(List.of("SKU-001", "SKU-002", "NOPE"))).extracting(ProductDTO::getSku)
                .containsExactlyInAnyOrder("SKU-001", "SKU-002");
    }

    @Test
    @DisplayName("a Redis outage never breaks the catalogue: reads and writes fall through to the database")
    void redisOutage() {
        ProductDTO created = create("SKU-001", "79.99");
        SharedRedis.pause();

        assertThat(service.getProductById(created.getId()).getSku()).isEqualTo("SKU-001");
        ProductDTO updated = service.updateProduct(created.getId(), UpdateProductRequest.builder().price(new BigDecimal("5.00")).build(), null);

        assertThat(updated.getPrice()).isEqualByComparingTo("5.00");
    }
}
