package com.ecommerce.orderservice.repository;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@PostgresIntegrationTest
@DisplayName("OrderRepository (PostgreSQL)")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orders;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
    }

    private Order newOrder(Long customerId, String sku, int quantity, String price) {
        return Order.place(customerId, "USD", List.of(
                OrderLine.builder().productId(sku).quantity(quantity).unitPrice(new BigDecimal(price)).build()));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("an order is stored with its lines, total, version and timestamps")
    void roundTrip() {
        Order saved = orders.save(newOrder(1L, "SKU-001", 3, "10.50"));
        flushAndClear();

        Order found = orders.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getTotalAmount()).isEqualByComparingTo("31.50");
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getLines()).singleElement().satisfies(l -> {
            assertThat(l.getProductId()).isEqualTo("SKU-001");
            assertThat(l.getQuantity()).isEqualTo(3);
            assertThat(l.getUnitPrice()).isEqualByComparingTo("10.50");
        });
    }

    @Test
    @DisplayName("each customer's orders are returned separately, paged and sorted")
    void findByCustomer() {
        orders.save(newOrder(1L, "SKU-001", 1, "1.00"));
        orders.save(newOrder(1L, "SKU-002", 1, "2.00"));
        orders.save(newOrder(2L, "SKU-003", 1, "3.00"));
        flushAndClear();

        assertThat(orders.findByCustomerId(1L, PageRequest.of(0, 10, Sort.by("totalAmount").descending())).getContent())
                .extracting(o -> o.getTotalAmount().intValue()).containsExactly(2, 1);
        assertThat(orders.findByCustomerId(2L, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(orders.findByCustomerId(3L, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    // PostgreSQL aborts the whole transaction on a constraint violation, so each rule gets its own test.

    @Test
    @DisplayName("the database itself refuses an unknown order status")
    void statusConstraint() {
        Order saved = orders.saveAndFlush(newOrder(1L, "SKU-001", 1, "1.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'SHIPPED' WHERE id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the database itself refuses a zero quantity")
    void quantityConstraint() {
        Order saved = orders.saveAndFlush(newOrder(1L, "SKU-001", 1, "1.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE order_lines SET quantity = 0 WHERE order_id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the database itself refuses a negative price")
    void priceConstraint() {
        Order saved = orders.saveAndFlush(newOrder(1L, "SKU-001", 1, "1.00"));

        assertThatThrownBy(() -> jdbc.update("UPDATE order_lines SET unit_price = -1 WHERE order_id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting an order deletes its lines")
    void cascade() {
        Order saved = orders.saveAndFlush(newOrder(1L, "SKU-001", 1, "1.00"));

        orders.delete(saved);
        orders.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_lines", Long.class)).isZero();
    }

    @Test
    @DisplayName("the saga deadline finds only open orders that have not changed since the cutoff, oldest first")
    void staleOpenOrders() {
        Order stale = orders.saveAndFlush(newOrder(1L, "SKU-001", 1, "1.00"));
        Order fresh = orders.saveAndFlush(newOrder(1L, "SKU-002", 1, "1.00"));
        Order doneButOld = newOrder(1L, "SKU-003", 1, "1.00");
        doneButOld.transitionTo(OrderStatus.COMPLETED);
        doneButOld = orders.saveAndFlush(doneButOld);
        // updated_at is maintained by a database trigger on every UPDATE; switch it off just to back-date rows.
        jdbc.execute("ALTER TABLE orders DISABLE TRIGGER orders_update_timestamp");
        jdbc.update("UPDATE orders SET updated_at = now() - interval '10 minutes' WHERE id IN (?, ?)", stale.getId(), doneButOld.getId());
        jdbc.execute("ALTER TABLE orders ENABLE TRIGGER orders_update_timestamp");
        flushAndClear();

        List<Long> ids = orders.findStaleOpenOrderIds(OrderStatus.open(), LocalDateTime.now().minusMinutes(5), PageRequest.of(0, 10));

        assertThat(ids).containsExactly(stale.getId()).doesNotContain(fresh.getId(), doneButOld.getId());
    }
}
