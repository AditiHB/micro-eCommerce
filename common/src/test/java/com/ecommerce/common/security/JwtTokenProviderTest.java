package com.ecommerce.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private Authentication authentication;

    private String testSecret;
    private long testExpiration;

    @BeforeEach
    void setUp() {
        testSecret = "mySecretKeyForJWTTokenSigningPurposeOnly12345678901234567890";
        testExpiration = 86400000L;
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", testExpiration);
    }

    @Test
    @DisplayName("Should generate token successfully")
    void testGenerateTokenSuccess() {
        when(authentication.getName()).thenReturn("testuser");

        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("Should generate token from username successfully")
    void testGenerateTokenFromUsernameSuccess() {
        String token = jwtTokenProvider.generateTokenFromUsername("testuser");

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("Should extract username from valid token")
    void testGetUsernameFromTokenSuccess() {
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.generateToken(authentication);

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should validate valid token")
    void testValidateTokenSuccess() {
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.generateToken(authentication);

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid token")
    void testValidateInvalidToken() {
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject null token")
    void testValidateNullToken() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken(null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should reject empty token")
    void testValidateEmptyToken() {
        boolean isValid = jwtTokenProvider.validateToken("");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle expired token")
    void testValidateExpiredToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1L);
        when(authentication.getName()).thenReturn("testuser");

        String token = jwtTokenProvider.generateToken(authentication);
        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should generate different tokens at different times")
    void testGenerateDifferentTokens() {
        when(authentication.getName()).thenReturn("testuser");

        String token1 = jwtTokenProvider.generateToken(authentication);
        String token2 = jwtTokenProvider.generateToken(authentication);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("Should extract username from token generated from username")
    void testGetUsernameFromGeneratedToken() {
        String token = jwtTokenProvider.generateTokenFromUsername("adminuser");

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("adminuser");
    }

    @Test
    @DisplayName("Should handle username with special characters")
    void testGenerateTokenWithSpecialCharacters() {
        String specialUsername = "user@example.com";
        String token = jwtTokenProvider.generateTokenFromUsername(specialUsername);

        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(extractedUsername).isEqualTo(specialUsername);
    }

    @Test
    @DisplayName("Should validate token generated from username")
    void testValidateTokenFromUsername() {
        String token = jwtTokenProvider.generateTokenFromUsername("testuser");

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject token with tampered payload")
    void testValidateTamperedToken() {
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        boolean isValid = jwtTokenProvider.validateToken(tamperedToken);

        assertThat(isValid).isFalse();
    }
}
