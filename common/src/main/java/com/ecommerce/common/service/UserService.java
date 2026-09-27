package com.ecommerce.common.service;

import com.ecommerce.common.dto.AuthResponse;
import com.ecommerce.common.dto.LoginRequest;
import com.ecommerce.common.entity.User;
import com.ecommerce.common.entity.UserRole;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.repository.UserRepository;
import com.ecommerce.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse login(LoginRequest request) {
        log.info("Authenticating user: {}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = tokenProvider.generateToken(authentication);
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        log.info("User {} authenticated successfully", request.getUsername());
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().toString())
                .build();
    }

    public User registerUser(String username, String email, String password) {
        log.info("Registering new user: {}", username);

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username already exists", "USERNAME_EXISTS");
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already exists", "EMAIL_EXISTS");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(UserRole.USER)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User {} registered successfully with ID: {}", username, savedUser.getId());
        return savedUser;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
    }
}
