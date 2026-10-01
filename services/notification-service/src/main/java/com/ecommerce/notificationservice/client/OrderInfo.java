package com.ecommerce.notificationservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal projection of Order Service's OrderResponse, used to resolve the
 * owning customer for events (like payment-processed/payment-failed) that
 * only carry an orderId.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderInfo {
    private Long id;
    private Long customerId;
}
