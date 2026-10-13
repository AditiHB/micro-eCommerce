package com.ecommerce.orderservice.batch;

import com.ecommerce.orderservice.Invoice;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * The invoice-generation batch job: one chunk-oriented step over completed, not-yet-invoiced orders.
 * Chunk size is 1 - each order is its own atomic unit (render, sign, compress, save, queue the event) -
 * which keeps the outbox write's transaction boundary easy to reason about for a low-volume job; a
 * failure on one order never rolls back any other order already written in the same run.
 *
 * <p>{@code spring.batch.job.enabled=false} (see application.yml) stops Spring Boot from auto-running
 * this job at every application startup - it only ever runs when {@link InvoiceJobScheduler} launches it.
 */
@Configuration
public class InvoiceBatchConfig {

    @Bean
    public RepositoryItemReader<Order> invoiceOrderReader(OrderRepository orderRepository) {
        return new RepositoryItemReaderBuilder<Order>()
                .name("invoiceOrderReader")
                .repository(orderRepository)
                .methodName("findCompletedWithoutInvoice")
                .pageSize(1)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public Step generateInvoicesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                     RepositoryItemReader<Order> invoiceOrderReader,
                                     ItemProcessor<Order, Invoice> invoiceItemProcessor,
                                     ItemWriter<Invoice> invoiceItemWriter) {
        return new StepBuilder("generateInvoicesStep", jobRepository)
                .<Order, Invoice>chunk(1, transactionManager)
                .reader(invoiceOrderReader)
                .processor(invoiceItemProcessor)
                .writer(invoiceItemWriter)
                .build();
    }

    @Bean
    public Job invoiceGenerationJob(JobRepository jobRepository, Step generateInvoicesStep) {
        return new JobBuilder("invoiceGenerationJob", jobRepository)
                .start(generateInvoicesStep)
                .build();
    }
}
