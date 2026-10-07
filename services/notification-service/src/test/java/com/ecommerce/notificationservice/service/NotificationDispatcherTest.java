package com.ecommerce.notificationservice.service;

import com.ecommerce.common.enums.NotificationStatus;
import com.ecommerce.common.enums.NotificationType;
import com.ecommerce.notificationservice.Notification;
import com.ecommerce.notificationservice.NotificationRepository;
import com.ecommerce.notificationservice.config.NotificationProperties;
import com.ecommerce.notificationservice.sender.NotificationDeliveryException;
import com.ecommerce.notificationservice.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatcher")
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private NotificationSender sender;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meters;

    private final NotificationProperties properties = new NotificationProperties();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        properties.setMaxAttempts(3);
        properties.setInitialBackoff(Duration.ofSeconds(5));
    }

    private NotificationDispatcher dispatcher(Executor executor) {
        return new NotificationDispatcher(repository, sender, properties, transactionManager, meters, executor);
    }

    private static Notification pending(long id, String recipient) {
        return Notification.builder()
                .id(id).customerId(1L).orderId(1L).type(NotificationType.PAYMENT_SUCCESS)
                .recipient(recipient).subject("Subject").message("Body")
                .status(NotificationStatus.PENDING).attempts(0).sourceEventId("evt-" + id)
                .build();
    }

    @Test
    @DisplayName("a notification that sends successfully is marked SENT")
    void sendsSuccessfully() {
        Notification notification = pending(1L, "jane@example.com");
        when(repository.lockDue(any(Instant.class), anyInt())).thenReturn(List.of(notification));
        doNothing().when(sender).send(anyString(), anyString(), anyString());

        int handled = dispatcher(Runnable::run).dispatchOnce();

        assertThat(handled).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("a failed send under the max attempts stays PENDING with backoff, not FAILED")
    void failedSendBacksOff() {
        Notification notification = pending(1L, "jane@example.com");
        when(repository.lockDue(any(Instant.class), anyInt())).thenReturn(List.of(notification));
        doThrow(new NotificationDeliveryException("SMTP unreachable")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher(Runnable::run).dispatchOnce();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getErrorMessage()).isEqualTo("SMTP unreachable");
        assertThat(notification.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("a failed send on the last allowed attempt is FAILED for good")
    void givesUpAfterMaxAttempts() {
        Notification notification = pending(1L, "jane@example.com");
        notification.setAttempts(2);
        when(repository.lockDue(any(Instant.class), anyInt())).thenReturn(List.of(notification));
        doThrow(new NotificationDeliveryException("mailbox does not exist")).when(sender).send(anyString(), anyString(), anyString());

        dispatcher(Runnable::run).dispatchOnce();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(3);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("a saturated send executor is recorded as a failed attempt with backoff, not thrown")
    void executorSaturationIsTreatedAsFailure() {
        Notification notification = pending(1L, "jane@example.com");
        when(repository.lockDue(any(Instant.class), anyInt())).thenReturn(List.of(notification));
        Executor rejecting = task -> {
            throw new RejectedExecutionException("pool saturated");
        };

        int handled = dispatcher(rejecting).dispatchOnce();

        assertThat(handled).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isAfter(Instant.now());
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("a batch of sends runs concurrently, not one at a time")
    void sendsRunConcurrently() {
        List<Notification> due = List.of(
                pending(1L, "a@example.com"), pending(2L, "b@example.com"),
                pending(3L, "c@example.com"), pending(4L, "d@example.com"));
        when(repository.lockDue(any(Instant.class), anyInt())).thenReturn(due);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            maxObserved.updateAndGet(max -> Math.max(max, inFlight.incrementAndGet()));
            Thread.sleep(50);
            inFlight.decrementAndGet();
            return null;
        }).when(sender).send(anyString(), anyString(), anyString());
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            dispatcher(pool).dispatchOnce();
        } finally {
            pool.shutdown();
        }

        assertThat(maxObserved.get()).isGreaterThan(1);
        assertThat(due).allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT));
    }
}
