package com.ecommerce.paymentservice.exception;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.exception.ConflictException;

/** The payment cannot go from where it is to where it was asked to go - e.g. refunding money never captured (409). */
public class InvalidPaymentTransitionException extends ConflictException {

    public InvalidPaymentTransitionException(Long paymentId, PaymentStatus from, PaymentStatus to) {
        super("Payment " + paymentId + " cannot move from " + from + " to " + to
                + (from.allowedNext().isEmpty() ? " (it is " + from + " and final)" : " (allowed: " + from.allowedNext() + ")"),
                "PAYMENT_INVALID_TRANSITION");
    }
}
