package com.ecommerce.notificationservice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerId(Long customerId, Pageable pageable);

    List<Notification> findByOrderId(Long orderId);

    Optional<Notification> findBySourceEventId(String sourceEventId);

    boolean existsBySourceEventId(String sourceEventId);
}
