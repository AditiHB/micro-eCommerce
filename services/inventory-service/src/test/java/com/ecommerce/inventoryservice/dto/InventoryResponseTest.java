package com.ecommerce.inventoryservice.dto;

import com.ecommerce.common.config.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventoryResponse wire format")
class InventoryResponseTest {

    @Test
    @DisplayName("carries the stock level and the version that doubles as the ETag")
    void wireFormat() {
        InventoryResponse response = InventoryResponse.builder().id(1L).productId("SKU-001").quantity(0).version(4L).build();

        JsonNode json = JacksonConfig.newObjectMapper().valueToTree(response);

        assertThat(json.get("productId").asText()).isEqualTo("SKU-001");
        assertThat(json.get("quantity").asInt()).isZero();
        assertThat(json.get("version").asLong()).isEqualTo(4L);
    }
}
