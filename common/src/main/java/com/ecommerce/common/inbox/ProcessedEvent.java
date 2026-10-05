package com.ecommerce.common.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** "This consumer has already applied this event" - the ledger behind {@link InboxService}. */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "consumer", nullable = false, length = 100)
        private String consumer;

        @Column(name = "event_id", nullable = false, length = 100)
        private String eventId;
    }
}
