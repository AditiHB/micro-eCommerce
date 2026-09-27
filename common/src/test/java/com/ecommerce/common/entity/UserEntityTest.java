package com.ecommerce.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Entity Unit Tests")
class UserEntityTest {

    @Test
    @DisplayName("Should create user with all fields")
    void testUserCreation() {
        User user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .enabled(true)
            .build();

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPassword()).isEqualTo("encoded_password");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should create admin user")
    void testAdminUserCreation() {
        User admin = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("admin_password")
            .role(UserRole.ADMIN)
            .enabled(true)
            .build();

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should allow user to be disabled")
    void testDisabledUser() {
        User user = User.builder()
            .username("inactive")
            .email("inactive@example.com")
            .password("password")
            .enabled(false)
            .build();

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should support empty user creation")
    void testEmptyUserCreation() {
        User user = new User();

        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isNull();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("Should allow username modification")
    void testUsernameModification() {
        User user = User.builder()
            .username("oldname")
            .build();

        user.setUsername("newname");

        assertThat(user.getUsername()).isEqualTo("newname");
    }

    @Test
    @DisplayName("Should allow email modification")
    void testEmailModification() {
        User user = User.builder()
            .email("old@example.com")
            .build();

        user.setEmail("new@example.com");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("Should allow role modification")
    void testRoleModification() {
        User user = User.builder()
            .role(UserRole.USER)
            .build();

        user.setRole(UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should handle null values")
    void testNullValues() {
        User user = User.builder()
            .id(null)
            .username(null)
            .email(null)
            .password(null)
            .role(null)
            .build();

        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isNull();
        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("Should handle special characters in username")
    void testSpecialCharactersInUsername() {
        User user = User.builder()
            .username("user@domain.com")
            .build();

        assertThat(user.getUsername()).isEqualTo("user@domain.com");
    }
}
