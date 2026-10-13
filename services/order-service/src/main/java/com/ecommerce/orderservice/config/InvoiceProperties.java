package com.ecommerce.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Invoice batch job behaviour that operations may tune ({@code ecommerce.invoices.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "ecommerce.invoices")
public class InvoiceProperties {

    /** How often the invoice batch job looks for completed, not-yet-invoiced orders. */
    private Duration pollInterval = Duration.ofMinutes(1);

    /** Switch the scheduler off (tests launch the job directly instead). */
    private boolean enabled = true;
}
