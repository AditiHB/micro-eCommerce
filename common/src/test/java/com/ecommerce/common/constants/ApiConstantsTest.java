package com.ecommerce.common.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ApiConstants Unit Tests")
class ApiConstantsTest {

    @Test
    @DisplayName("Should verify API constants are defined")
    void testApiConstantsNotNull() {
        assertThat(ApiConstants.API_PREFIX).isNotNull();
        assertThat(ApiConstants.CUSTOMERS_ENDPOINT).isNotNull();
    }

    @Test
    @DisplayName("Should verify API prefix has correct format")
    void testApiPrefixFormat() {
        assertThat(ApiConstants.API_PREFIX).startsWith("/");
    }

    @Test
    @DisplayName("Should verify MAX_PAGE_SIZE is positive")
    void testMaxPageSizeIsPositive() {
        assertThat(ApiConstants.MAX_PAGE_SIZE).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should verify DEFAULT_PAGE_SIZE is positive")
    void testDefaultPageSizeIsPositive() {
        assertThat(ApiConstants.DEFAULT_PAGE_SIZE).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should verify DEFAULT_PAGE_SIZE is less than or equal to MAX_PAGE_SIZE")
    void testDefaultPageSizeNotExceedingMax() {
        assertThat(ApiConstants.DEFAULT_PAGE_SIZE).isLessThanOrEqualTo(ApiConstants.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("Should access API endpoints")
    void testAccessApiEndpoints() {
        assertThat(ApiConstants.CUSTOMERS_ENDPOINT).isNotBlank();
    }

    @Test
    @DisplayName("Should have consistent endpoint format")
    void testEndpointConsistency() {
        assertThat(ApiConstants.CUSTOMERS_ENDPOINT).startsWith("/");
    }
}
