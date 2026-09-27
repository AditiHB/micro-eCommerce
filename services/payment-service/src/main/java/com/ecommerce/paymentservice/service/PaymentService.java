package com.ecommerce.paymentservice.service;

import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.exception.EventPublishingException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String KAFKA_TOPIC_PAYMENT_PROCESSED = "payment-processed";
    private static final String KAFKA_TOPIC_PAYMENT_FAILED = "payment-failed";

    /**
     * Processes a payment and publishes an event.
     *
     * @param request the payment processing request
     * @return the processed payment response
     */
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
    public PaymentResponse getPayment(Long id) {
        log.info("Fetching payment with ID: {}", id);

        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        return mapToResponse(payment);
    }

    /**
     * Retrieves all payments.
     *
     * @return list of payment responses
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        log.info("Fetching all payments");
        return paymentRepository.findAll()
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    /**
     * Refunds a payment.
     *
     * @param id the payment ID
     * @return the refunded payment response
     * @throws ResourceNotFoundException if payment not found
     */
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
     * @throws EventPublishingException if publishing fails
     */
    private void publishPaymentProcessedEvent(Payment payment) {
        try {
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount()
            );

            Message<PaymentProcessedEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, KAFKA_TOPIC_PAYMENT_PROCESSED)
                .build();

            kafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentProcessedEvent for payment {}", payment.getId(), ex);
                        throw new EventPublishingException("Failed to publish payment processed event", ex);
                    } else {
                        log.info("PaymentProcessedEvent published successfully for payment {}", payment.getId());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing PaymentProcessedEvent", e);
            throw new EventPublishingException("Failed to publish payment processed event", e);
        }
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
