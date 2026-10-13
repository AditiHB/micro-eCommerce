package com.ecommerce.orderservice.integration;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.outbox.OutboxRepository;
import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.orderservice.Invoice;
import com.ecommerce.orderservice.InvoiceRepository;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.OrderRepository;
import com.ecommerce.orderservice.client.CustomerInfo;
import com.ecommerce.orderservice.client.InvoiceCustomerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The invoice batch job against a real PostgreSQL with the real migrations (including
 * V12__Spring_Batch_Schema.sql, Spring Batch's own JobRepository tables): render, sign, compress three
 * ways, persist, and queue the outbox event - then confirm a second run does not reprocess the order.
 */
@SpringBootTest
@PostgresIntegrationTest
@SpringBatchTest
@DisplayName("Invoice batch job (PostgreSQL)")
class InvoiceBatchIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @MockBean
    private InvoiceCustomerClient customerClient;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        orderRepository.deleteAll();
        outboxRepository.deleteAll();
        when(customerClient.find(7L)).thenReturn(Optional.of(new CustomerInfo(7L, "Jane Doe", "jane@example.com")));
    }

    private Order completedOrder() {
        Order order = Order.builder()
                .customerId(7L)
                .status(OrderStatus.COMPLETED)
                .currency("USD")
                .totalAmount(new BigDecimal("79.99"))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(OrderLine.builder().order(order).lineNo(1).productId("SKU-001")
                .quantity(1).unitPrice(new BigDecimal("79.99")).build());
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("generates, signs, compresses three ways, persists and queues the event for a completed order")
    void generatesInvoiceForCompletedOrder() throws Exception {
        Order order = completedOrder();

        JobExecution execution = jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Invoice invoice = invoiceRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(invoice.getCustomerId()).isEqualTo(7L);
        assertThat(invoice.getPdfGzip()).isNotEmpty();
        assertThat(invoice.getPdfZstd()).isNotEmpty();
        assertThat(invoice.getPdfBrotli()).isNotEmpty();
        assertThat(invoice.getSignature()).isNotEmpty();
        assertThat(invoice.getContentSha256()).hasSize(64);

        assertThat(outboxRepository.findAll()).filteredOn(e -> e.getEventType().equals("invoice.generated"))
                .singleElement()
                .satisfies(event -> assertThat(event.getAggregateId()).isEqualTo(String.valueOf(order.getId())));
    }

    @Test
    @DisplayName("a second run does not reprocess an order that already has an invoice")
    void doesNotReprocessAlreadyInvoicedOrders() throws Exception {
        completedOrder();
        jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParameters());
        assertThat(invoiceRepository.count()).isEqualTo(1);

        jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).filteredOn(e -> e.getEventType().equals("invoice.generated")).hasSize(1);
    }

    @Test
    @DisplayName("orders that are not COMPLETED are left alone")
    void ignoresOrdersNotCompleted() throws Exception {
        Order order = Order.builder()
                .customerId(7L)
                .status(OrderStatus.PENDING)
                .currency("USD")
                .totalAmount(new BigDecimal("10.00"))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(OrderLine.builder().order(order).lineNo(1).productId("SKU-001")
                .quantity(1).unitPrice(BigDecimal.TEN).build());
        orderRepository.save(order);

        jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(invoiceRepository.count()).isZero();
    }
}
