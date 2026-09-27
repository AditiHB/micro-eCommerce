package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AuthResponse DTO Unit Tests")
class AuthResponseTest {

    @Test
    @DisplayName("Should create AuthResponse with builder")
    void testAuthResponseBuilder() {
        AuthResponse response = AuthResponse.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .token("jwt_token_123")
            .role("USER")
            .build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getToken()).isEqualTo("jwt_token_123");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should create empty AuthResponse")
    void testEmptyAuthResponse() {
        AuthResponse response = new AuthResponse();

        assertThat(response.getId()).isNull();
        assertThat(response.getUsername()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getToken()).isNull();
        assertThat(response.getRole()).isNull();
    }

    @Test
    @DisplayName("Should allow setter modifications")
    void testAuthResponseSetters() {
        AuthResponse response = new AuthResponse();
        response.setId(1L);
        response.setUsername("testuser");
        response.setEmail("test@example.com");
        response.setToken("token");
        response.setRole("ADMIN");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should handle null values")
    void testAuthResponseNullValues() {
        AuthResponse response = AuthResponse.builder()
            .id(null)
            .username(null)
            .email(null)
            .token(null)
            .role(null)
            .build();

        assertThat(response.getId()).isNull();
        assertThat(response.getUsername()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getToken()).isNull();
        assertThat(response.getRole()).isNull();
    }

    @Test
    @DisplayName("Should handle special characters in fields")
    void testAuthResponseSpecialCharacters() {
        AuthResponse response = AuthResponse.builder()
            .username("user@example.com")
            .email("user+test@example.com")
            .token("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")
            .role("SUPER_ADMIN")
            .build();

        assertThat(response.getUsername()).isEqualTo("user@example.com");
        assertThat(response.getEmail()).isEqualTo("user+test@example.com");
        assertThat(response.getToken()).startsWith("eyJ");
        assertThat(response.getRole()).isEqualTo("SUPER_ADMIN");
    }
}
