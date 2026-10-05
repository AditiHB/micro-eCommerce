package com.ecommerce.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lifecycle state machines")
class EnumTest {

    @Test
    @DisplayName("an order moves forward through the saga, or is cancelled or failed from any open status")
    void orderForwardTransitions() {
        assertThat(OrderStatus.PENDING.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_PROCESSING,
                        OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED);
        assertThat(OrderStatus.INVENTORY_RESERVED.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.PAYMENT_PROCESSING, OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED);
        assertThat(OrderStatus.PAYMENT_PROCESSING.allowedNext())
                .containsExactlyInAnyOrder(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED);
    }

    @Test
    @DisplayName("COMPLETED, CANCELLED and FAILED are final: nothing leaves them (a late payment cannot revive a cancelled order)")
    void orderTerminalStatesAreFinal() {
        for (OrderStatus terminal : EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED)) {
            assertThat(terminal.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).as("%s -> %s", terminal, next).isFalse();
            }
        }
    }

    @Test
    @DisplayName("an order can never go backwards")
    void orderCannotGoBackwards() {
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.PAYMENT_PROCESSING.canTransitionTo(OrderStatus.INVENTORY_RESERVED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("the open statuses are exactly the non-terminal ones")
    void openStatuses() {
        assertThat(OrderStatus.open()).containsExactlyInAnyOrder(
                OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_PROCESSING);
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStatus.open().contains(status)).isEqualTo(!status.isTerminal());
        }
    }

    @Test
    @DisplayName("money moves PENDING -> AUTHORIZED -> CAPTURED -> REFUNDED, and only a captured payment can be refunded")
    void paymentLifecycle() {
        assertThat(PaymentStatus.PENDING.allowedNext()).containsExactlyInAnyOrder(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED);
        assertThat(PaymentStatus.AUTHORIZED.allowedNext()).containsExactlyInAnyOrder(PaymentStatus.CAPTURED, PaymentStatus.FAILED);
        assertThat(PaymentStatus.CAPTURED.allowedNext()).containsExactly(PaymentStatus.REFUNDED);
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse();
    }

    @Test
    @DisplayName("REFUNDED and FAILED payments are final")
    void paymentTerminalStates() {
        for (PaymentStatus terminal : EnumSet.of(PaymentStatus.REFUNDED, PaymentStatus.FAILED)) {
            assertThat(terminal.allowedNext()).isEmpty();
        }
    }
}
