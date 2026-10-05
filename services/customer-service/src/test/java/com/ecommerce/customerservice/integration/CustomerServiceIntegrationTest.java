package com.ecommerce.customerservice.integration;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.common.testsupport.SharedRedis;
import com.ecommerce.customerservice.CustomerRepository;
import com.ecommerce.customerservice.dto.CreateCustomerRequest;
import com.ecommerce.customerservice.dto.CustomerResponse;
import com.ecommerce.customerservice.dto.UpdateCustomerRequest;
import com.ecommerce.customerservice.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customer cache against a real Redis and a real database: only reference data is cached, one entry per
 * customer, evicted by key (not wholesale) after the change commits - and a Redis outage degrades to the database
 * instead of failing requests.
 */
@SpringBootTest
@PostgresIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Customer cache (Redis + PostgreSQL)")
class CustomerServiceIntegrationTest {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", SharedRedis::host);
        registry.add("spring.data.redis.port", SharedRedis::port);
    }

    @Autowired
    private CustomerService service;
    @Autowired
    private CustomerRepository repository;
    @Autowired
    private CacheManager cacheManager;

    private Cache cache() {
        return cacheManager.getCache(CacheConfig.CUSTOMERS_CACHE);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        cache().clear();
    }

    @AfterEach
    void tearDown() {
        SharedRedis.resume();
    }

    private long create(String name, String email) {
        return service.createCustomer(CreateCustomerRequest.builder().name(name).email(email).build()).getId();
    }

    @Test
    @DisplayName("a customer read by id is cached: the second read does not need the database")
    void readsAreCached() {
        long id = create("John Doe", "john@example.com");

        service.getCustomer(id);
        assertThat(cache().get(id)).isNotNull();

        repository.deleteAllInBatch(); // the row is gone, but the cached entry still answers
        assertThat(service.getCustomer(id).getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("updating a customer evicts that customer's entry - and only that one")
    void evictionIsPerKey() {
        long john = create("John Doe", "john@example.com");
        long jane = create("Jane Doe", "jane@example.com");
        service.getCustomer(john);
        service.getCustomer(jane);

        service.updateCustomer(john, UpdateCustomerRequest.builder().name("Johnny Doe").email("john@example.com").build(), null);

        assertThat(cache().get(john)).as("the changed customer is evicted").isNull();
        assertThat(cache().get(jane)).as("the others stay cached").isNotNull();
        assertThat(service.getCustomer(john).getName()).as("the next read sees the change").isEqualTo("Johnny Doe");
    }

    @Test
    @DisplayName("deleting a customer evicts the entry, so a deleted customer is not served from the cache")
    void deleteEvicts() {
        long id = create("John Doe", "john@example.com");
        service.getCustomer(id);

        service.deleteCustomer(id);

        assertThat(cache().get(id)).isNull();
    }

    @Test
    @DisplayName("lists are not cached")
    void listsAreNotCached() {
        create("John Doe", "john@example.com");

        service.getAllCustomers(0, 10, "id");

        assertThat(cache().get("all:0:10:id")).isNull();
    }

    @Test
    @DisplayName("the cached entry carries the version, so the ETag it yields matches the database")
    void cachedVersion() {
        long id = create("John Doe", "john@example.com");
        service.updateCustomer(id, UpdateCustomerRequest.builder().name("John Q").email("john@example.com").build(), null);

        CustomerResponse first = service.getCustomer(id);
        CustomerResponse second = service.getCustomer(id);

        assertThat(first.getVersion()).isEqualTo(1L);
        assertThat(second.getVersion()).isEqualTo(first.getVersion());
    }

    @Test
    @DisplayName("a Redis outage never breaks a request: reads fall through to the database, writes still succeed")
    void redisOutageDegrades() {
        long id = create("John Doe", "john@example.com");
        SharedRedis.pause();

        CustomerResponse read = service.getCustomer(id);
        long other = create("Jane Doe", "jane@example.com");
        service.updateCustomer(other, UpdateCustomerRequest.builder().name("Jane Q").email("jane@example.com").build(), null);

        assertThat(read.getName()).isEqualTo("John Doe");
        assertThat(repository.findById(other).orElseThrow().getName()).isEqualTo("Jane Q");
    }
}
