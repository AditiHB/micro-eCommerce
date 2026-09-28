package com.ecommerce.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestResponseLoggingFilter Unit Tests")
class RequestResponseLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private RequestResponseLoggingFilter filter;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should pass request through filter chain")
    void testFilterPassthrough() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should log request method")
    void testLogRequestMethod() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(request, response, chain);

        verify(request).getMethod();
    }

    @Test
    @DisplayName("Should log request URI")
    void testLogRequestUri() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/customers/123");

        filter.doFilter(request, response, chain);

        verify(request).getRequestURI();
    }

    @Test
    @DisplayName("Should handle multiple filter calls")
    void testMultipleRequests() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should handle exceptions from filter chain")
    void testHandleChainException() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        doThrow(new RuntimeException("Chain error")).when(chain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception e) {
            // Expected
        }

        verify(chain).doFilter(any(), any());
    }
}
