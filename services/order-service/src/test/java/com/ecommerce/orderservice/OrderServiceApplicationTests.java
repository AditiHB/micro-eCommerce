package com.ecommerce.orderservice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test ensures the Spring context loads successfully, 
        // validating configuration and dependency injection.
    }

}
