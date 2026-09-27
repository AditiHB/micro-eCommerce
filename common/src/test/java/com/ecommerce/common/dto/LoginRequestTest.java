package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LoginRequest DTO Tests")
class LoginRequestTest {

    @Test
    @DisplayName("Should create request with valid credentials")
    void testValidLoginRequest() {
        LoginRequest request = LoginRequest.builder()
            .username("john_doe")
            .password("securePassword123")
            .build();

        assertThat(request.getUsername()).isEqualTo("john_doe");
        assertThat(request.getPassword()).isEqualTo("securePassword123");
    }

    @Test
    @DisplayName("Should update username via setter")
    void testSetUsername() {
        LoginRequest request = new LoginRequest();
        request.setUsername("jane_doe");

        assertThat(request.getUsername()).isEqualTo("jane_doe");
    }

    @Test
    @DisplayName("Should update password via setter")
    void testSetPassword() {
        LoginRequest request = new LoginRequest();
        request.setPassword("newPassword456");

        assertThat(request.getPassword()).isEqualTo("newPassword456");
    }

    @Test
    @DisplayName("Should support all-args constructor")
    void testAllArgsConstructor() {
        LoginRequest request = new LoginRequest("admin", "adminPass789");

        assertThat(request.getUsername()).isEqualTo("admin");
        assertThat(request.getPassword()).isEqualTo("adminPass789");
    }

    @Test
    @DisplayName("Should support no-args constructor")
    void testNoArgsConstructor() {
        LoginRequest request = new LoginRequest();

        assertThat(request.getUsername()).isNull();
        assertThat(request.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should handle special characters in credentials")
    void testSpecialCharacters() {
        LoginRequest request = LoginRequest.builder()
            .username("user@example.com")
            .password("P@ssw0rd!#$%^&*()")
            .build();

        assertThat(request.getUsername()).isEqualTo("user@example.com");
        assertThat(request.getPassword()).isEqualTo("P@ssw0rd!#$%^&*()");
    }

    @Test
    @DisplayName("Should handle empty credentials")
    void testEmptyCredentials() {
        LoginRequest request = LoginRequest.builder()
            .username("")
            .password("")
            .build();

        assertThat(request.getUsername()).isEmpty();
        assertThat(request.getPassword()).isEmpty();
    }
}
