package com.ecommerce.notificationservice.repository;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@PostgresIntegrationTest
@DisplayName("NotificationRepository (PostgreSQL)")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    private Notification notification(String eventId, Long customerId, Long orderId, NotificationStatus status, Instant due) {
        return Notification.builder().customerId(customerId).orderId(orderId).type(NotificationType.ORDER_CREATED)
                .recipient("jane@example.com").subject("Subject").message("Body").status(status)
                .sourceEventId(eventId).nextAttemptAt(due).build();
    }

    @Test
    @DisplayName("notifications are found by customer (paged) and by order")
    void queries() {
        repository.save(notification("evt-1", 1L, 100L, NotificationStatus.SENT, null));
        repository.save(notification("evt-2", 1L, 101L, NotificationStatus.SENT, null));
        repository.save(notification("evt-3", 2L, 102L, NotificationStatus.SENT, null));

        assertThat(repository.findByCustomerId(1L, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(repository.findByOrderId(102L)).hasSize(1);
        assertThat(repository.existsBySourceEventId("evt-1")).isTrue();
        assertThat(repository.existsBySourceEventId("nope")).isFalse();
    }

    @Test
    @DisplayName("the same source event cannot be recorded twice: the database enforces idempotency")
    void sourceEventIsUnique() {
        repository.saveAndFlush(notification("evt-1", 1L, 100L, NotificationStatus.PENDING, Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(notification("evt-1", 2L, 200L, NotificationStatus.PENDING, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("only PENDING notifications that are due are locked for delivery, oldest first")
    void lockDue() {
        Instant now = Instant.now();
        repository.save(notification("due-late", 1L, 1L, NotificationStatus.PENDING, now.minusSeconds(10)));
        repository.save(notification("due-early", 1L, 2L, NotificationStatus.PENDING, now.minusSeconds(60)));
        repository.save(notification("future", 1L, 3L, NotificationStatus.PENDING, now.plusSeconds(600)));
        repository.save(notification("sent", 1L, 4L, NotificationStatus.SENT, now.minusSeconds(60)));
        repository.save(notification("failed", 1L, 5L, NotificationStatus.FAILED, now.minusSeconds(60)));
        entityManager.flush();
        entityManager.clear();

        List<Notification> due = repository.lockDue(now, 10);

        assertThat(due).extracting(Notification::getSourceEventId).containsExactly("due-early", "due-late");
    }

    @Test
    @DisplayName("the delivery attempt counters round-trip")
    void attempts() {
        Notification saved = repository.saveAndFlush(notification("evt-1", 1L, 1L, NotificationStatus.PENDING, Instant.now()));
        saved.setAttempts(3);
        repository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getAttempts()).isEqualTo(3);
    }
}
