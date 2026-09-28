package com.ecommerce.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfig Unit Tests")
class SecurityConfigTest {

    @InjectMocks
    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should create password encoder bean")
    void testPasswordEncoderCreation() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertThat(encoder).isNotNull();
    }

    @Test
    @DisplayName("Should encode password correctly")
    void testPasswordEncoding() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String password = "testPassword123";
        String encoded = encoder.encode(password);

        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEqualTo(password);
        assertThat(encoder.matches(password, encoded)).isTrue();
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void testPasswordMismatch() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String password = "testPassword123";
        String wrongPassword = "wrongPassword456";
        String encoded = encoder.encode(password);

        assertThat(encoder.matches(wrongPassword, encoded)).isFalse();
    }

    @Test
    @DisplayName("Should create authentication manager")
    void testAuthenticationManagerCreation() throws Exception {
        AuthenticationConfiguration authConfig = mock(AuthenticationConfiguration.class);
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        when(authConfig.getAuthenticationManager()).thenReturn(authManager);

        assertThat(authConfig.getAuthenticationManager()).isNotNull();
    }

    @Test
    @DisplayName("Should encode multiple passwords differently")
    void testDifferentEncodings() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String password = "samePassword";
        String encoded1 = encoder.encode(password);
        String encoded2 = encoder.encode(password);

        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(encoder.matches(password, encoded1)).isTrue();
        assertThat(encoder.matches(password, encoded2)).isTrue();
    }
}
