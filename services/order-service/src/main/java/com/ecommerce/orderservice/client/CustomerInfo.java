package com.ecommerce.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal projection of Customer Service's CustomerResponse, used only to put a name and email on an
 * invoice. Deserialization ignores any fields we don't care about, so this stays resilient to unrelated
 * changes in Customer Service's response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerInfo {
    private Long id;
    private String name;
    private String email;
}
