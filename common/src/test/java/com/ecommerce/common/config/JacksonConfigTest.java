package com.ecommerce.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("JacksonConfig Integration Tests")
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should create ObjectMapper bean")
    void testObjectMapperBeanCreation() {
        assertThat(objectMapper).isNotNull();
    }

    @Test
    @DisplayName("Should serialize objects to JSON")
    void testSerialization() throws Exception {
        TestData data = new TestData("test", 123);
        String json = objectMapper.writeValueAsString(data);

        assertThat(json).contains("test").contains("123");
    }

    @Test
    @DisplayName("Should deserialize JSON to objects")
    void testDeserialization() throws Exception {
        String json = "{\"name\":\"test\",\"value\":123}";
        TestData data = objectMapper.readValue(json, TestData.class);

        assertThat(data.getName()).isEqualTo("test");
        assertThat(data.getValue()).isEqualTo(123);
    }

    static class TestData {
        private String name;
        private int value;

        public TestData() {}
        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public int getValue() { return value; }
    }
}
