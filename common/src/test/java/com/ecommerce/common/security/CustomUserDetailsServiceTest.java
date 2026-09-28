package com.ecommerce.common.security;

import com.ecommerce.common.entity.User;
import com.ecommerce.common.entity.UserRole;
import com.ecommerce.common.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .enabled(true)
            .build();
    }

    @Test
    @DisplayName("Should load user details successfully")
    void testLoadUserByUsernameSuccess() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("testuser");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByUsername("nonexistent"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should handle null username")
    void testLoadUserByNullUsername() {
        when(userRepository.findByUsername(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Should handle empty username")
    void testLoadUserByEmptyUsername() {
        when(userRepository.findByUsername("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Should load admin user details")
    void testLoadAdminUserDetails() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("admin_password")
            .role(UserRole.ADMIN)
            .enabled(true)
            .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("admin");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Should load disabled user")
    void testLoadDisabledUserDetails() {
        User disabledUser = User.builder()
            .id(3L)
            .username("disabled")
            .email("disabled@example.com")
            .password("password")
            .role(UserRole.USER)
            .enabled(false)
            .build();

        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(disabledUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("disabled");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should handle special characters in username")
    void testLoadUserWithSpecialCharacters() {
        User specialUser = User.builder()
            .id(4L)
            .username("user@example.com")
            .email("user@example.com")
            .password("password")
            .role(UserRole.USER)
            .enabled(true)
            .build();

        when(userRepository.findByUsername("user@example.com")).thenReturn(Optional.of(specialUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("user@example.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("user@example.com");
    }
}
