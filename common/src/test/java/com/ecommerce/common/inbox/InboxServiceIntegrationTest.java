package com.ecommerce.common.inbox;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.common.testsupport.TestMessagingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestMessagingApplication.class)
@PostgresIntegrationTest
@DisplayName("Idempotent consumer (inbox)")
class InboxServiceIntegrationTest {

    @Autowired
    private InboxService inbox;
    @Autowired
    private ProcessedEventRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        repository.deleteAll();
    }

    private boolean claim(String consumer, String eventId) {
        return Boolean.TRUE.equals(tx.execute(s -> inbox.firstDelivery(consumer, eventId)));
    }

    @Test
    @DisplayName("the first delivery is processed, a redelivery is skipped")
    void duplicateDeliveryIsSkipped() {
        assertThat(claim("inventory-group", "evt-1")).isTrue();
        assertThat(claim("inventory-group", "evt-1")).isFalse();
        assertThat(claim("inventory-group", "evt-1")).isFalse();
    }

    @Test
    @DisplayName("each consumer keeps its own ledger: one service having handled an event does not skip another")
    void consumersAreIndependent() {
        assertThat(claim("inventory-group", "evt-1")).isTrue();
        assertThat(claim("payment-group", "evt-1")).isTrue();
        assertThat(claim("payment-group", "evt-2")).isTrue();
    }

    @Test
    @DisplayName("the claim rolls back with a failed handler, so the retry processes the event instead of skipping it")
    void failedHandlerReleasesItsClaim() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            assertThat(inbox.firstDelivery("order-group", "evt-9")).isTrue();
            throw new IllegalStateException("handler failed after claiming");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
        assertThat(claim("order-group", "evt-9")).as("the retry").isTrue();
    }

    @Test
    @DisplayName("claiming outside a transaction is a programming error")
    void needsATransaction() {
        assertThatThrownBy(() -> inbox.firstDelivery("order-group", "evt-1"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("old claims are purged, recent ones are kept")
    void purgesOldClaims() {
        tx.executeWithoutResult(s -> {
            inbox.firstDelivery("order-group", "old");
            inbox.firstDelivery("order-group", "recent");
        });
        ProcessedEvent old = repository.findAll().stream().filter(p -> p.getId().getEventId().equals("old")).findFirst().orElseThrow();
        old.setProcessedAt(Instant.now().minus(Duration.ofDays(30)));
        repository.save(old);

        inbox.purgeOld();

        assertThat(repository.findAll()).extracting(p -> p.getId().getEventId()).containsExactly("recent");
    }
}
