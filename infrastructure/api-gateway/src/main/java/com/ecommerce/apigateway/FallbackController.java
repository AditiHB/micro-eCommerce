package com.ecommerce.apigateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @GetMapping("/fallback/service")
    public String serviceFallback() {
        return "The requested service is currently unavailable. Please try again later. (Circuit Breaker Activated)";
    }
}
