package com.ecommerce.orderservice.batch;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.orderservice.Order;
import com.ecommerce.orderservice.OrderLine;
import com.ecommerce.orderservice.client.CustomerInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoicePdfRenderer")
class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    @DisplayName("the rendered PDF's text names the order, the customer and the total")
    void rendersOrderDetails() throws Exception {
        Order order = Order.builder()
                .id(42L)
                .customerId(7L)
                .status(OrderStatus.COMPLETED)
                .currency("USD")
                .totalAmount(new BigDecimal("172.97"))
                .lines(List.of(OrderLine.builder()
                        .lineNo(1).productId("SKU-001").quantity(2).unitPrice(new BigDecimal("79.99")).build()))
                .build();
        CustomerInfo customer = new CustomerInfo(7L, "Jane Doe", "jane@example.com");

        byte[] pdf = renderer.render(order, customer);

        String text;
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Order #42")
                .contains("Jane Doe")
                .contains("jane@example.com")
                .contains("SKU-001")
                .contains("172.97")
                .contains("USD");
    }
}
