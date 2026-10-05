package com.ecommerce.orderservice.exception;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.exception.ConflictException;

/** The order cannot go from where it is to where it was asked to go (409). */
public class InvalidOrderTransitionException extends ConflictException {

    public InvalidOrderTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Order " + orderId + " cannot move from " + from + " to " + to
                + (from.isTerminal() ? " (it is " + from + " and final)" : " (allowed: " + from.allowedNext() + ")"),
                "ORDER_INVALID_TRANSITION");
    }

    public InvalidOrderTransitionException(String message, String errorCode) {
        super(message, errorCode);
    }
}
