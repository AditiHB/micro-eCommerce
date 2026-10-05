package com.ecommerce.customerservice.dto;

import com.ecommerce.common.config.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer DTOs")
class CustomerResponseTest {

    @Test
    @DisplayName("a customer response carries the version that doubles as the ETag")
    void responseWireFormat() {
        CustomerResponse response = CustomerResponse.builder().id(1L).name("John").email("john@example.com").version(3L).build();

        JsonNode json = JacksonConfig.newObjectMapper().valueToTree(response);

        assertThat(json.get("email").asText()).isEqualTo("john@example.com");
        assertThat(json.get("version").asLong()).isEqualTo(3L);
    }

    @Test
    @DisplayName("the update request is its own type and does not accept an id")
    void updateRequestShape() throws Exception {
        var mapper = JacksonConfig.newObjectMapper();

        UpdateCustomerRequest ok = mapper.readValue("{\"name\":\"Jane\",\"email\":\"jane@example.com\"}", UpdateCustomerRequest.class);

        assertThat(ok.getName()).isEqualTo("Jane");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> mapper.readValue("{\"name\":\"Jane\",\"email\":\"jane@example.com\",\"id\":99}", UpdateCustomerRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
    }
}
