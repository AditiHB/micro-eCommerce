package com.ecommerce.notificationservice.integration;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.common.inbox.ProcessedEventRepository;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.config.NotificationProperties;
import com.ecommerce.notificationservice.sender.NotificationDeliveryException;
import com.ecommerce.notificationservice.sender.NotificationSender;
import com.ecommerce.notificationservice.service.NotificationDispatcher;
import com.ecommerce.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Notification recording and delivery against a real PostgreSQL: delivery is decoupled from the event, retried
 * with backoff when the mail server is down, given up on after the maximum, and never sends a notification twice
 * even with several dispatchers running.
 */
@SpringBootTest
@PostgresIntegrationTest
@DisplayName("Notification delivery (PostgreSQL)")
class NotificationDeliveryIntegrationTest {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private NotificationRepository repository;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private NotificationProperties properties;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meters;

    @MockBean
    private NotificationSender sender;

    private NotificationDispatcher dispatcher;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        processed.deleteAll();
        reset(sender);
        properties.setMaxAttempts(3);
        properties.setBatchSize(25);
        properties.setInitialBackoff(Duration.ofSeconds(5));
        dispatcher = new NotificationDispatcher(repository, sender, properties, transactionManager, meters, Runnable::run);
        tx = new TransactionTemplate(transactionManager);
    }

    private void record(String eventId, String email) {
        tx.executeWithoutResult(s -> notifications.record(eventId, 7L, 42L, NotificationType.PAYMENT_SUCCESS, "Subject " + eventId, "Body", email));
    }

    private Notification only() {
        return repository.findAll().get(0);
    }

    @Test
    @DisplayName("recording writes PENDING without sending anything: the event handler never waits for the mail server")
    void recordingDoesNotSend() {
        record("evt-1", "jane@example.com");

        assertThat(only().getStatus()).isEqualTo(NotificationStatus.PENDING);
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("the dispatcher delivers a pending notification and marks it SENT")
    void delivers() {
        record("evt-1", "jane@example.com");

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        verify(sender).send("jane@example.com", "Subject evt-1", "Body");
        Notification notification = only();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(dispatcher.dispatchOnce()).as("nothing left").isZero();
    }

    @Test
    @DisplayName("a mail-server outage keeps the notification PENDING with a later retry time - it is not lost")
    void outageIsRetriedLater() {
        record("evt-1", "jane@example.com");
        doThrow(new NotificationDeliveryException("SMTP unreachable")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchOnce();

        Notification notification = only();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isEqualTo("SMTP unreachable");
        assertThat(notification.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(dispatcher.dispatchOnce()).as("not due yet").isZero();
    }

    @Test
    @DisplayName("when the mail server comes back, the same notification is delivered on a later pass")
    void recoversAfterOutage() {
        record("evt-1", "jane@example.com");
        doThrow(new NotificationDeliveryException("SMTP unreachable")).when(sender).send(anyString(), anyString(), anyString());
        dispatcher.dispatchOnce();
        Notification failed = only();
        failed.setNextAttemptAt(Instant.now().minusSeconds(1));
        repository.save(failed);
        doNothing().when(sender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchOnce();

        assertThat(only().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(only().getAttempts()).isEqualTo(2);
        verify(sender, times(2)).send(eq("jane@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("after the maximum attempts it is FAILED for good, with the last error")
    void givesUp() {
        record("evt-1", "jane@example.com");
        doThrow(new NotificationDeliveryException("mailbox does not exist")).when(sender).send(anyString(), anyString(), anyString());

        for (int i = 0; i < 3; i++) {
            Notification n = only();
            n.setNextAttemptAt(Instant.now().minusSeconds(1));
            repository.save(n);
            dispatcher.dispatchOnce();
        }

        Notification notification = only();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(3);
        assertThat(notification.getErrorMessage()).isEqualTo("mailbox does not exist");
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("a redelivered event records one notification, not two")
    void recordingIsIdempotent() {
        record("evt-1", "jane@example.com");
        record("evt-1", "jane@example.com");

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("several dispatchers at once deliver each notification exactly once")
    void concurrentDispatchersSendOnce() throws Exception {
        int count = 40;
        for (int i = 0; i < count; i++) {
            record("evt-" + i, "user" + i + "@example.com");
        }
        Map<String, AtomicInteger> sends = new ConcurrentHashMap<>();
        org.mockito.Mockito.doAnswer(inv -> {
            sends.computeIfAbsent(inv.getArgument(0), k -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(3);
            return null;
        }).when(sender).send(anyString(), anyString(), anyString());
        properties.setBatchSize(8);
        List<NotificationDispatcher> dispatchers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            dispatchers.add(new NotificationDispatcher(repository, sender, properties, transactionManager, meters, Runnable::run));
        }

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        for (NotificationDispatcher d : dispatchers) {
            pool.submit(() -> {
                start.await();
                while (d.dispatchOnce() > 0) {
                    // keep draining
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        while (dispatchers.get(0).dispatchOnce() > 0) {
            // pick up anything a peer still had locked
        }

        assertThat(sends).hasSize(count);
        assertThat(sends.values()).allSatisfy(n -> assertThat(n.get()).isEqualTo(1));
        assertThat(repository.countByStatus(NotificationStatus.SENT)).isEqualTo(count);
    }
}
