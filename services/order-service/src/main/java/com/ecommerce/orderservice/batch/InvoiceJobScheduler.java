package com.ecommerce.orderservice.batch;

import com.ecommerce.orderservice.config.InvoiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Launches {@code invoiceGenerationJob} on a schedule. Each run gets a fresh {@code runAt} parameter so
 * Spring Batch treats it as a new, distinct {@code JobInstance} - without it, the second scheduled run
 * would see identical job parameters to the first and refuse to run again ("already complete").
 */
@Component
@ConditionalOnProperty(prefix = "ecommerce.invoices", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class InvoiceJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job invoiceGenerationJob;
    private final InvoiceProperties properties;

    @Scheduled(fixedDelayString = "${ecommerce.invoices.poll-interval:PT1M}")
    public void run() {
        try {
            jobLauncher.run(invoiceGenerationJob, new JobParametersBuilder()
                    .addLocalDateTime("runAt", LocalDateTime.now())
                    .toJobParameters());
        } catch (JobExecutionException | RuntimeException e) {
            // A database hiccup or a Spring Batch infrastructure error must not kill the scheduler
            // thread; the next poll (in poll-interval, see InvoiceProperties) tries again.
            log.error("Invoice batch job run failed", e);
        }
    }
}
