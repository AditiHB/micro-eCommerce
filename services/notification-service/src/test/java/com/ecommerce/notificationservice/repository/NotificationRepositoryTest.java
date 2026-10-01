package com.ecommerce.notificationservice.repository;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("NotificationRepository Tests")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    private Notification newNotification(Long customerId, Long orderId, String sourceEventId) {
        return Notification.builder()
            .customerId(customerId)
            .orderId(orderId)
            .type(NotificationType.ORDER_CREATED)
            .recipient("customer@example.com")
            .subject("Subject")
            .message("Message")
            .status(NotificationStatus.SENT)
            .sourceEventId(sourceEventId)
            .build();
    }

    @Test
    @DisplayName("Should find notifications by customer id")
    void testFindByCustomerId() {
        repository.save(newNotification(1L, 100L, "evt-1"));
        repository.save(newNotification(1L, 101L, "evt-2"));
        repository.save(newNotification(2L, 102L, "evt-3"));

        Pageable pageable = PageRequest.of(0, 10);
        var page = repository.findByCustomerId(1L, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find notifications by order id")
    void testFindByOrderId() {
        repository.save(newNotification(1L, 100L, "evt-1"));

        List<Notification> notifications = repository.findByOrderId(100L);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getOrderId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should find a notification by source event id")
    void testFindBySourceEventId() {
        repository.save(newNotification(1L, 100L, "evt-unique"));

        Optional<Notification> found = repository.findBySourceEventId("evt-unique");

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("Should report existence by source event id")
    void testExistsBySourceEventId() {
        repository.save(newNotification(1L, 100L, "evt-exists"));

        assertThat(repository.existsBySourceEventId("evt-exists")).isTrue();
        assertThat(repository.existsBySourceEventId("evt-missing")).isFalse();
    }

    @Test
    @DisplayName("Should enforce uniqueness of source event id")
    void testSourceEventIdUnique() {
        repository.saveAndFlush(newNotification(1L, 100L, "evt-dup"));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            repository.saveAndFlush(newNotification(2L, 200L, "evt-dup")));
    }
}
