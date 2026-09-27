package com.ecommerce.paymentservice.service;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.enums.PaymentStatus;
import com.ecommerce.common.events.EventPublisher;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.eventsourcing.EventSourcingService;
import com.ecommerce.paymentservice.Payment;
import com.ecommerce.paymentservice.PaymentRepository;
import com.ecommerce.paymentservice.dto.ProcessPaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private EventSourcingService eventSourcingService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;
    private ProcessPaymentRequest processRequest;

    @BeforeEach
    void setUp() {
        testPayment = Payment.builder()
            .id(1L)
            .orderId("order-123")
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        processRequest = ProcessPaymentRequest.builder()
            .orderId("order-123")
            .amount(BigDecimal.valueOf(99.99))
            .build();
    }

    @Test
    @DisplayName("Should process payment successfully")
    void testProcessPaymentSuccess() {
        Payment processingPayment = Payment.builder()
            .id(1L)
            .orderId("order-123")
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSING)
            .build();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(processingPayment)
            .thenReturn(testPayment);
        doNothing().when(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), anyString());

        PaymentResponse response = paymentService.processPayment(processRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-123");
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(99.99));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(eventPublisher, times(1)).publishEvent(any(PaymentProcessedEvent.class), anyString());
    }

    @Test
    @DisplayName("Should get payment by ID successfully")
    void testGetPaymentSuccess() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        PaymentResponse response = paymentService.getPayment(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-123");
        verify(paymentRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when payment not found")
    void testGetPaymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should get all payments with pagination")
    void testGetAllPaymentsSuccess() {
        List<Payment> payments = Arrays.asList(testPayment);
        Page<Payment> page = new PageImpl<>(payments, mock(Pageable.class), 1);
        when(paymentRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<PaymentResponse> response = paymentService.getAllPayments(0, 10, "id");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should refund payment successfully")
    void testRefundPaymentSuccess() {
        Payment refundedPayment = Payment.builder()
            .id(1L)
            .orderId("order-123")
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.REFUNDED)
            .build();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(refundedPayment);

        PaymentResponse response = paymentService.refundPayment(1L);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should throw exception when refunding non-existent payment")
    void testRefundPaymentNotFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle multiple payment statuses")
    void testMultiplePaymentStatusTransitions() {
        Payment payment = Payment.builder()
            .id(1L)
            .orderId("order-123")
            .amount(BigDecimal.valueOf(99.99))
            .status(PaymentStatus.PROCESSING)
            .build();

        when(paymentRepository.findById(1L))
            .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(Payment.builder().id(1L).status(PaymentStatus.PROCESSED).build())
            .thenReturn(Payment.builder().id(1L).status(PaymentStatus.REFUNDED).build());

        paymentService.refundPayment(1L);

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should enforce max page size")
    void testGetAllPaymentsMaxPageSize() {
        List<Payment> payments = Arrays.asList(testPayment);
        Page<Payment> page = new PageImpl<>(payments, mock(Pageable.class), 1);
        when(paymentRepository.findAll(any(Pageable.class))).thenReturn(page);

        paymentService.getAllPayments(0, 1000, "id");

        verify(paymentRepository, times(1)).findAll(argThat(pageable ->
            pageable.getPageSize() <= ApiConstants.MAX_PAGE_SIZE
        ));
    }

    @Test
    @DisplayName("Should handle empty payments list")
    void testGetAllPaymentsEmpty() {
        Page<Payment> emptyPage = new PageImpl<>(Arrays.asList(), mock(Pageable.class), 0);
        when(paymentRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<PaymentResponse> response = paymentService.getAllPayments(0, 10, "id");

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should publish event when payment is processed")
    void testPaymentEventPublished() {
        Payment processingPayment = Payment.builder()
            .id(1L)
            .status(PaymentStatus.PROCESSING)
            .build();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(processingPayment)
            .thenReturn(testPayment);
        doNothing().when(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), anyString());

        paymentService.processPayment(processRequest);

        verify(eventPublisher, times(1)).publishEvent(
            argThat(event -> event instanceof PaymentProcessedEvent),
            eq("payment-processed")
        );
    }

    @Test
    @DisplayName("Should handle small payment amounts")
    void testProcessSmallPayment() {
        ProcessPaymentRequest smallRequest = ProcessPaymentRequest.builder()
            .orderId("order-123")
            .amount(BigDecimal.valueOf(0.01))
            .build();

        Payment smallPayment = Payment.builder()
            .id(1L)
            .amount(BigDecimal.valueOf(0.01))
            .status(PaymentStatus.PROCESSED)
            .build();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(smallPayment)
            .thenReturn(smallPayment);
        doNothing().when(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), anyString());

        PaymentResponse response = paymentService.processPayment(smallRequest);

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(0.01));
    }

    @Test
    @DisplayName("Should handle large payment amounts")
    void testProcessLargePayment() {
        ProcessPaymentRequest largeRequest = ProcessPaymentRequest.builder()
            .orderId("order-123")
            .amount(BigDecimal.valueOf(999999.99))
            .build();

        Payment largePayment = Payment.builder()
            .id(1L)
            .amount(BigDecimal.valueOf(999999.99))
            .status(PaymentStatus.PROCESSED)
            .build();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(largePayment)
            .thenReturn(largePayment);
        doNothing().when(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), anyString());

        PaymentResponse response = paymentService.processPayment(largeRequest);

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(999999.99));
    }

    @Test
    @DisplayName("Should handle zero payment amount")
    void testProcessZeroPayment() {
        ProcessPaymentRequest zeroRequest = ProcessPaymentRequest.builder()
            .orderId("order-123")
            .amount(BigDecimal.valueOf(0.00))
            .build();

        Payment zeroPayment = Payment.builder()
            .id(1L)
            .amount(BigDecimal.valueOf(0.00))
            .status(PaymentStatus.PROCESSED)
            .build();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(zeroPayment)
            .thenReturn(zeroPayment);
        doNothing().when(eventPublisher).publishEvent(any(PaymentProcessedEvent.class), anyString());

        PaymentResponse response = paymentService.processPayment(zeroRequest);

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(0.00));
    }
}
