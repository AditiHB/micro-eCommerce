package com.ecommerce.common.service;

import com.ecommerce.common.dto.AuthResponse;
import com.ecommerce.common.dto.LoginRequest;
import com.ecommerce.common.entity.User;
import com.ecommerce.common.entity.UserRole;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.repository.UserRepository;
import com.ecommerce.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private LoginRequest loginRequest;

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

        loginRequest = LoginRequest.builder()
            .username("testuser")
            .password("password123")
            .build();
    }

    @Test
    @DisplayName("Should login user successfully")
    void testLoginSuccess() {
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication))
            .thenReturn("jwt_token_123");
        when(userRepository.findByUsername("testuser"))
            .thenReturn(Optional.of(testUser));

        AuthResponse response = userService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt_token_123");
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getId()).isEqualTo(1L);
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    @DisplayName("Should throw exception when user not found during login")
    void testLoginUserNotFound() {
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenReturn(authentication);
        when(userRepository.findByUsername("testuser"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should register user successfully")
    void testRegisterUserSuccess() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User result = userService.registerUser("newuser", "new@example.com", "password123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.isEnabled()).isTrue();
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("password123");
    }

    @Test
    @DisplayName("Should throw exception when username already exists")
    void testRegisterUserUsernameExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("testuser", "new@example.com", "password123"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void testRegisterUserEmailExists() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("newuser", "test@example.com", "password123"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("Should get user by ID successfully")
    void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        User result = userService.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should throw exception when user not found by ID")
    void testGetUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should get user by username successfully")
    void testGetUserByUsernameSuccess() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        User result = userService.getUserByUsername("testuser");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should throw exception when user not found by username")
    void testGetUserByUsernameNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("nonexistent"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should handle null inputs in register")
    void testRegisterUserWithNullInputs() {
        when(userRepository.existsByUsername(null)).thenReturn(false);
        when(userRepository.existsByEmail(null)).thenReturn(false);

        assertThatThrownBy(() -> userService.registerUser(null, null, null))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should handle empty strings in login")
    void testLoginWithEmptyCredentials() {
        LoginRequest emptyRequest = LoginRequest.builder()
            .username("")
            .password("")
            .build();

        when(authenticationManager.authenticate(any()))
            .thenThrow(new RuntimeException("Invalid credentials"));

        assertThatThrownBy(() -> userService.login(emptyRequest))
            .isInstanceOf(RuntimeException.class);
    }
}
