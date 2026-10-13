package com.ecommerce.orderservice.batch;

import com.aayushatharva.brotli4j.decoder.Decoder;
import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceCompressor")
class InvoiceCompressorTest {

    private final InvoiceCompressor compressor = new InvoiceCompressor();

    // Long and repetitive on purpose: real compression ratios only show up on compressible data: a
    // few random bytes can legitimately come out of any codec larger than they went in.
    private static final byte[] DATA = ("INVOICE - Order #1\n".repeat(50)).getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("gzip round-trips and shrinks repetitive data")
    void gzipRoundTrips() throws Exception {
        byte[] compressed = compressor.gzip(DATA);

        assertThat(compressed).hasSizeLessThan(DATA.length);
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            assertThat(in.readAllBytes()).isEqualTo(DATA);
        }
    }

    @Test
    @DisplayName("zstd round-trips and shrinks repetitive data")
    void zstdRoundTrips() {
        byte[] compressed = compressor.zstd(DATA);

        assertThat(compressed).hasSizeLessThan(DATA.length);
        byte[] decompressed = Zstd.decompress(compressed, (int) Zstd.getFrameContentSize(compressed));
        assertThat(decompressed).isEqualTo(DATA);
    }

    @Test
    @DisplayName("brotli round-trips and shrinks repetitive data")
    void brotliRoundTrips() throws Exception {
        byte[] compressed = compressor.brotli(DATA);

        assertThat(compressed).hasSizeLessThan(DATA.length);
        byte[] decompressed = Decoder.decompress(compressed).getDecompressedData();
        assertThat(decompressed).isEqualTo(DATA);
    }
}
