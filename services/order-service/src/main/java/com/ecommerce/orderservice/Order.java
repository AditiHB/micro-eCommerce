package com.ecommerce.orderservice;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The order aggregate: who ordered, what (priced lines whose unit prices were snapshotted from the catalogue when
 * the order was placed), the total, and where it is in its lifecycle. Everything that changes an order goes
 * through here, so the rules live in one place: the status only moves along the edges of
 * {@link OrderStatus}, and {@code version} stops two concurrent changes from silently overwriting each other.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "lines")
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Customer ID cannot be null")
    @Column(nullable = false)
    private Long customerId;

    @Setter(AccessLevel.NONE)
    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @NotNull
    @Column(nullable = false, length = 3)
    private String currency;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Optimistic lock and the resource's ETag. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<OrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** A new, pending order for the given priced lines; the total is computed here, never supplied by a caller. */
    public static Order place(Long customerId, String currency, List<OrderLine> pricedLines) {
        Order order = Order.builder()
                .customerId(customerId)
                .currency(currency)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();
        int lineNo = 1;
        for (OrderLine line : pricedLines) {
            line.setOrder(order);
            line.setLineNo(lineNo++);
            order.lines.add(line);
        }
        order.totalAmount = order.lines.stream()
                .map(OrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order;
    }

    /** Moves the order along the lifecycle, or refuses (409) if that edge does not exist. */
    public void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderTransitionException(id, status, next);
        }
        this.status = next;
    }

    public boolean isOpen() {
        return !status.isTerminal();
    }
}
