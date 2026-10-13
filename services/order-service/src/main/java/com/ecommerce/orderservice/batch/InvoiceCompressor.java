package com.ecommerce.orderservice.batch;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.github.luben.zstd.Zstd;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

/**
 * Compresses the same PDF bytes three different ways, so the invoice table demonstrates all three
 * codecs side by side: gzip (JDK built-in, DEFLATE), zstd and brotli (both native, loaded from the
 * platform-specific jars added to this service's pom).
 */
@Component
public class InvoiceCompressor {

    public InvoiceCompressor() {
        Brotli4jLoader.ensureAvailability();
    }

    public byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException("gzip compression failed", e);
        }
        return out.toByteArray();
    }

    public byte[] zstd(byte[] data) {
        return Zstd.compress(data);
    }

    public byte[] brotli(byte[] data) {
        try {
            return Encoder.compress(data);
        } catch (IOException e) {
            throw new UncheckedIOException("brotli compression failed", e);
        }
    }
}
