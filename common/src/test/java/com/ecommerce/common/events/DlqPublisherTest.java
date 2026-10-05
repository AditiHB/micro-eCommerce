package com.ecommerce.common.events;

import com.ecommerce.common.config.JacksonConfig;
import com.ecommerce.common.dlq.DeadLetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqPublisher (dead-letter replay)")
class DlqPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafka;

    private DlqPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DlqPublisher(kafka, JacksonConfig.newObjectMapper());
    }

    private DeadLetter parked() {
        return DeadLetter.builder().id(5L).originalTopic("order-created").messageKey("42").payload("{\"orderId\":42}")
                .headers("{\"__TypeId__\":\"order.created\",\"eventId\":\"evt-1\"}")
                .consumerGroup("inventory-group").status(DeadLetter.PARKED).build();
    }

    @Test
    @DisplayName("sends the parked message back to its original topic with its key and headers")
    @SuppressWarnings("unchecked")
    void replaysToTheOriginalTopic() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        publisher.replay(parked());

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        ProducerRecord<String, String> record = sent.getValue();
        assertThat(record.topic()).isEqualTo("order-created");
        assertThat(record.key()).isEqualTo("42");
        assertThat(record.value()).isEqualTo("{\"orderId\":42}");
        assertThat(new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8)).isEqualTo("order.created");
        assertThat(new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8)).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("fails (so the letter stays parked) when the broker does not acknowledge")
    @SuppressWarnings("unchecked")
    void failsWhenNotAcknowledged() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> publisher.replay(parked()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not replay dead letter 5");
    }
}
