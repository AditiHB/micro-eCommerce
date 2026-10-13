package com.ecommerce.orderservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * The generated invoice for one COMPLETED order: the same PDF stored three times, compressed
 * differently (gzip, zstd, brotli), plus the signature over the uncompressed bytes. Written once by the
 * batch job ({@code InvoiceItemWriter}); never updated afterwards.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"pdfGzip", "pdfZstd", "pdfBrotli", "signature"})
@EqualsAndHashCode(of = "id")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pdf_gzip", nullable = false, columnDefinition = "BYTEA")
    private byte[] pdfGzip;

    @Column(name = "pdf_zstd", nullable = false, columnDefinition = "BYTEA")
    private byte[] pdfZstd;

    @Column(name = "pdf_brotli", nullable = false, columnDefinition = "BYTEA")
    private byte[] pdfBrotli;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] signature;

    @Column(name = "signing_key_id", nullable = false, length = 100)
    private String signingKeyId;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false, length = 20)
    private String status;
}
