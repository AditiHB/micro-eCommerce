package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * A stand-in payment processor - there is no real one behind this project. It behaves like one where it matters
 * to the saga: it is idempotent (references are derived from the idempotency key, so a repeated call returns the
 * same answer), it declines (any amount above {@code payments.simulator.decline-above}, or an unsupported
 * currency), and it is stateless so any replica gives the same verdict.
 *
 * <p>DEVELOPMENT AND TEST ONLY. Before taking real money, implement {@link PaymentGateway} for the real
 * processor and select it with {@code payments.gateway.type} (this bean is only active for {@code simulated},
 * the default).
 */
@Component
@ConditionalOnProperty(prefix = "payments.gateway", name = "type", havingValue = "simulated", matchIfMissing = true)
@Slf4j
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR");

    private final BigDecimal declineAbove;

    public SimulatedPaymentGateway(@Value("${payments.simulator.decline-above:10000.00}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public Outcome authorize(String idempotencyKey, BigDecimal amount, String currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            return Outcome.declined("Currency " + currency + " is not supported");
        }
        if (amount.compareTo(declineAbove) > 0) {
            log.info("Simulated processor declines {} {} (above the {} limit)", amount, currency, declineAbove);
            return Outcome.declined("Card declined: amount exceeds the available limit");
        }
        return Outcome.approved(reference("auth", idempotencyKey));
    }

    @Override
    public Outcome capture(String idempotencyKey, String authorizationReference) {
        return Outcome.approved(reference("cap", idempotencyKey));
    }

    @Override
    public Outcome refund(String idempotencyKey, String captureReference, BigDecimal amount) {
        return Outcome.approved(reference("ref", idempotencyKey));
    }

    private static String reference(String kind, String idempotencyKey) {
        return "sim_" + kind + "_" + UUID.nameUUIDFromBytes((kind + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString().substring(0, 13);
    }
}
