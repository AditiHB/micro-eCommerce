package com.ecommerce.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * The payment processor, as this service sees it (a port: the business logic depends on this interface, never on
 * a particular processor). A real integration - Stripe, Adyen, a bank - implements it; the bundled
 * {@link SimulatedPaymentGateway} stands in so the saga can be exercised end to end.
 *
 * <p>Every call carries an <em>idempotency key</em>. Calling again with the same key must not charge or refund a
 * second time but return the first result - that is what makes a retry after a lost response (or a rolled-back
 * transaction around a successful call) safe.
 *
 * <p>A <em>decline</em> is a normal business outcome and is returned as a result. An exception means the
 * processor could not be reached or did not answer - the caller retries; nothing is known to have happened.
 */
public interface PaymentGateway {

    /** Reserves the amount on the customer's payment method. */
    Outcome authorize(String idempotencyKey, BigDecimal amount, String currency);

    /** Takes the money that {@link #authorize} reserved. */
    Outcome capture(String idempotencyKey, String authorizationReference);

    /** Gives captured money back. */
    Outcome refund(String idempotencyKey, String captureReference, BigDecimal amount);

    /**
     * What the processor decided.
     *
     * @param approved  true if the operation succeeded
     * @param reference the processor's id for it (to reference in later calls and in support queries)
     * @param declineReason why it was refused, when it was
     */
    record Outcome(boolean approved, String reference, String declineReason) {
        public static Outcome approved(String reference) {
            return new Outcome(true, reference, null);
        }

        public static Outcome declined(String reason) {
            return new Outcome(false, null, reason);
        }
    }
}
