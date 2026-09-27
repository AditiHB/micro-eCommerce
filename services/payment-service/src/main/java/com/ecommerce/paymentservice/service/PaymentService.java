package com.ecommerce.paymentservice.service;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final EventSourcingService eventSourcingService;

    private static final String KAFKA_TOPIC_PAYMENT_PROCESSED = "payment-processed";
    private static final String KAFKA_TOPIC_PAYMENT_FAILED = "payment-failed";

    /**
     * Processes a payment and publishes an event.
     *
     * @param request the payment processing request
     * @return the processed payment response
     */
    @CacheEvict(value = CacheConfig.PAYMENTS_CACHE, allEntries = true)
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment for order: {}, amount: {}", request.getOrderId(), request.getAmount());

        Payment payment = Payment.builder()
            .orderId(request.getOrderId())
            .amount(request.getAmount())
            .status(PaymentStatus.PROCESSING)
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Simulate payment processing (in real scenario, call payment gateway)
        savedPayment.setStatus(PaymentStatus.PROCESSED);
        Payment processedPayment = paymentRepository.save(savedPayment);

        log.info("Payment processed successfully with ID: {}", processedPayment.getId());
        publishPaymentProcessedEvent(processedPayment);

        return mapToResponse(processedPayment);
    }

    /**
     * Retrieves a payment by ID.
     *
     * @param id the payment ID
     * @return the payment response
     * @throws ResourceNotFoundException if payment not found
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PAYMENTS_CACHE, key = "#id")
    public PaymentResponse getPayment(Long id) {
        log.info("Fetching payment with ID: {}", id);

        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        return mapToResponse(payment);
    }

    /**
     * Retrieves all payments with pagination.
     *
     * @param pageNumber the page number (0-indexed)
     * @param pageSize the page size
     * @param sortBy the field to sort by
     * @return paged payment responses
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PAYMENTS_CACHE, key = "'all:' + #pageNumber + ':' + #pageSize + ':' + #sortBy")
    public PagedResponse<PaymentResponse> getAllPayments(int pageNumber, int pageSize, String sortBy) {
        log.info("Fetching payments - page: {}, size: {}, sortBy: {}", pageNumber, pageSize, sortBy);

        pageSize = Math.min(pageSize, ApiConstants.MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(sortBy).ascending());
        Page<Payment> page = paymentRepository.findAll(pageable);

        List<PaymentResponse> responses = page.getContent()
            .stream()
            .map(this::mapToResponse)
            .toList();

        return PagedResponse.of(responses, pageNumber, pageSize, page.getTotalElements());
    }

    /**
     * Refunds a payment.
     *
     * @param id the payment ID
     * @return the refunded payment response
     * @throws ResourceNotFoundException if payment not found
     */
    @CacheEvict(value = CacheConfig.PAYMENTS_CACHE, allEntries = true)
    public PaymentResponse refundPayment(Long id) {
        log.info("Processing refund for payment: {}", id);

        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment refundedPayment = paymentRepository.save(payment);

        log.info("Payment refunded successfully");
        return mapToResponse(refundedPayment);
    }

    /**
     * Publishes payment processed event to Kafka.
     *
     * @param payment the processed payment
     */
    private void publishPaymentProcessedEvent(Payment payment) {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount()
        );

        eventPublisher.publishEvent(event, KAFKA_TOPIC_PAYMENT_PROCESSED);
        log.info("PaymentProcessedEvent published successfully for payment {}", payment.getId());
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .orderId(payment.getOrderId())
            .amount(payment.getAmount())
            .status(payment.getStatus())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }
}
