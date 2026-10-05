package com.ecommerce.paymentservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.RefundCompletedEvent;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnprocessableEntityException;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The one place money moves. Charging happens only here, driven by the saga ({@link PaymentSagaHandler});
 * there is no REST endpoint that creates a charge, so there is exactly one owner of the charge command and no
 * second path to reconcile. Back office can read payments and refund a captured one.
 *
 * <p>The processor is called with a stable idempotency key per order, so if the surrounding transaction is
 * rolled back after the processor said yes, the retry gets the same answer instead of charging twice. Workflow
 * state (payment status) is never cached.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "orderId", "amount", "status", "createdAt", "updatedAt");

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final EventPublisher eventPublisher;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        return mapToResponse(paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id)));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long orderId) {
        return mapToResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order " + orderId + " not found")));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> getAllPayments(int pageNumber, int pageSize, String sortBy) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BusinessException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS, "INVALID_SORT_FIELD");
        }
        int size = Math.min(Math.max(pageSize, 1), ApiConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, Sort.by(sortBy).ascending());
        Page<Payment> page = paymentRepository.findAll(pageable);
        List<PaymentResponse> responses = page.getContent().stream().map(this::mapToResponse).toList();
        return PagedResponse.of(responses, pageNumber, size, page.getTotalElements());
    }

    // ------------------------------------------------------------------ the charge (saga only)

    /**
     * Charges an order: authorize, then capture. A processor <em>decline</em> is a normal outcome - the payment
     * becomes FAILED and {@code payment.failed} is announced (which cancels the order). If the processor cannot be
     * reached the exception propagates, nothing is saved, and the Kafka error handler retries.
     * The caller guarantees no payment exists for the order yet.
     */
    public Payment charge(Long orderId, Long customerId, BigDecimal amount, String currency) {
        Payment payment = paymentRepository.saveAndFlush(Payment.pending(orderId, customerId, amount, currency));

        PaymentGateway.Outcome authorization = gateway.authorize("order-" + orderId, amount, currency);
        if (!authorization.approved()) {
            return decline(payment, authorization.declineReason());
        }
        payment.authorized(authorization.reference());

        PaymentGateway.Outcome capture = gateway.capture("order-" + orderId, authorization.reference());
        if (!capture.approved()) {
            return decline(payment, capture.declineReason());
        }
        payment.captured(capture.reference());
        paymentRepository.saveAndFlush(payment);

        eventPublisher.publish(new PaymentProcessedEvent(payment.getId(), orderId, customerId, amount, currency));
        log.info("Payment {} captured for order {}: {} {}", payment.getId(), orderId, amount, currency);
        return payment;
    }

    private Payment decline(Payment payment, String reason) {
        payment.failed(reason);
        paymentRepository.saveAndFlush(payment);
        eventPublisher.publish(new PaymentFailedEvent(payment.getOrderId(), payment.getCustomerId(), reason));
        log.warn("Payment {} for order {} declined: {}", payment.getId(), payment.getOrderId(), reason);
        return payment;
    }

    // ------------------------------------------------------------------ refunds

    /** Back-office refund. Only a CAPTURED payment can be refunded; anything else is a 409. */
    public PaymentResponse refundPayment(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        return mapToResponse(refund(payment));
    }

    /**
     * Gives the captured money back through the processor (idempotently, keyed by the payment), marks the payment
     * REFUNDED and announces {@code refund.completed}. Refusing an illegal refund is the state machine's job: a
     * payment that is not CAPTURED throws.
     */
    Payment refund(Payment payment) {
        payment.requireRefundable();
        String key = "refund-" + payment.getId();
        PaymentGateway.Outcome outcome = gateway.refund(key, payment.getProcessorReference(), payment.getAmount());
        if (!outcome.approved()) {
            throw new UnprocessableEntityException("The payment processor refused the refund: " + outcome.declineReason(), "REFUND_DECLINED");
        }
        payment.refunded(outcome.reference());
        paymentRepository.saveAndFlush(payment);
        eventPublisher.publish(new RefundCompletedEvent(payment.getOrderId(), payment.getId(), payment.getCustomerId(),
                payment.getAmount(), payment.getCurrency()));
        log.info("Payment {} refunded ({} {})", payment.getId(), payment.getAmount(), payment.getCurrency());
        return payment;
    }

    PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .processorReference(payment.getProcessorReference())
                .failureReason(payment.getFailureReason())
                .version(payment.getVersion())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
