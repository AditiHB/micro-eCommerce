package com.ecommerce.orderservice.batch;

import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.client.CustomerInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Renders one order as a one-page PDF: who it was billed to, each line, the total, and when it was
 * generated. Deliberately simple - a fixed, short set of text rows at fixed y-offsets - since this is a
 * demonstration of the pipeline (render -> compress -> sign), not a templating engine; an order with
 * enough lines to overflow one page is out of scope.
 */
@Component
public class InvoicePdfRenderer {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    private static final float LEFT_MARGIN = 50;
    private static final float LINE_HEIGHT = 18;

    public byte[] render(Order order, CustomerInfo customer) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float y = PDRectangle.A4.getHeight() - 60;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                y = text(content, PDType1Font.HELVETICA_BOLD, 18, y, "INVOICE - Order #" + order.getId());
                y -= LINE_HEIGHT / 2;
                y = text(content, PDType1Font.HELVETICA, 11, y,
                        "Billed to: " + customer.getName() + " <" + customer.getEmail() + ">");
                y = text(content, PDType1Font.HELVETICA, 11, y,
                        "Generated: " + java.time.Instant.now().atZone(ZoneOffset.UTC).format(TIMESTAMP));
                y -= LINE_HEIGHT;

                y = text(content, PDType1Font.HELVETICA_BOLD, 11, y,
                        String.format("%-24s %8s %12s %12s", "Product", "Qty", "Unit price", "Line total"));
                for (OrderLine line : order.getLines()) {
                    y = text(content, PDType1Font.HELVETICA, 11, y, String.format("%-24s %8d %12s %12s",
                            line.getProductId(), line.getQuantity(), line.getUnitPrice(), line.getLineTotal()));
                }

                y -= LINE_HEIGHT / 2;
                text(content, PDType1Font.HELVETICA_BOLD, 12, y,
                        "Total: " + order.getTotalAmount() + " " + order.getCurrency());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render invoice PDF for order " + order.getId(), e);
        }
    }

    private static float text(PDPageContentStream content, PDType1Font font, float size, float y, String line) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(LEFT_MARGIN, y);
        content.showText(line);
        content.endText();
        return y - LINE_HEIGHT;
    }
}
