package com.ecommerce.inventoryservice;

import com.ecommerce.inventoryservice.dto.CreateInventoryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Inventory Controller Integration Tests")
class InventoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create inventory successfully")
    void testCreateInventorySuccess() throws Exception {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(100)
            .build();

        mockMvc.perform(post("/api/inventory")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.productId").value("PROD-001"))
            .andExpect(jsonPath("$.quantity").value(100))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("Should fail to create inventory with blank product ID")
    void testCreateInventoryBlankProductId() throws Exception {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("")
            .quantity(100)
            .build();

        mockMvc.perform(post("/api/inventory")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.productId").exists());
    }

    @Test
    @DisplayName("Should fail to create inventory with invalid quantity")
    void testCreateInventoryInvalidQuantity() throws Exception {
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId("PROD-001")
            .quantity(0)
            .build();

        mockMvc.perform(post("/api/inventory")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("Should get inventory by ID")
    void testGetInventoryById() throws Exception {
        Inventory inventory = Inventory.builder()
            .productId("PROD-002")
            .quantity(250)
            .build();
        Inventory savedInventory = inventoryRepository.save(inventory);

        mockMvc.perform(get("/api/inventory/" + savedInventory.getId())
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedInventory.getId()))
            .andExpect(jsonPath("$.productId").value("PROD-002"))
            .andExpect(jsonPath("$.quantity").value(250));
    }

    @Test
    @DisplayName("Should return 404 when inventory not found")
    void testGetInventoryNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(containsString("Inventory not found")));
    }

    @Test
    @DisplayName("Should get all inventory items with pagination")
    void testGetAllInventoryWithPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Inventory inventory = Inventory.builder()
                .productId("PROD-" + i)
                .quantity(i * 50)
                .build();
            inventoryRepository.save(inventory);
        }

        mockMvc.perform(get("/api/inventory?page=0&size=2&sortBy=id")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.pageSize").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.isFirst").value(true));
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void testReserveStockSuccess() throws Exception {
        Inventory inventory = Inventory.builder()
            .productId("PROD-003")
            .quantity(100)
            .build();
        Inventory savedInventory = inventoryRepository.save(inventory);

        mockMvc.perform(post("/api/inventory/" + savedInventory.getId() + "/reserve?quantity=30")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(70))
            .andExpect(jsonPath("$.id").value(savedInventory.getId()));
    }

    @Test
    @DisplayName("Should fail to reserve stock with insufficient quantity")
    void testReserveStockInsufficientQuantity() throws Exception {
        Inventory inventory = Inventory.builder()
            .productId("PROD-004")
            .quantity(50)
            .build();
        Inventory savedInventory = inventoryRepository.save(inventory);

        mockMvc.perform(post("/api/inventory/" + savedInventory.getId() + "/reserve?quantity=100")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("Should release stock successfully")
    void testReleaseStockSuccess() throws Exception {
        Inventory inventory = Inventory.builder()
            .productId("PROD-005")
            .quantity(70)
            .build();
        Inventory savedInventory = inventoryRepository.save(inventory);

        mockMvc.perform(post("/api/inventory/" + savedInventory.getId() + "/release?quantity=20")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(90));
    }

    @Test
    @DisplayName("Should update inventory quantity")
    void testUpdateInventoryQuantity() throws Exception {
        Inventory inventory = Inventory.builder()
            .productId("PROD-006")
            .quantity(150)
            .build();
        Inventory savedInventory = inventoryRepository.save(inventory);

        mockMvc.perform(put("/api/inventory/" + savedInventory.getId() + "?quantity=200")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(200));
    }

    @Test
    @DisplayName("Should handle pagination navigation")
    void testPaginationNavigation() throws Exception {
        for (int i = 1; i <= 30; i++) {
            Inventory inventory = Inventory.builder()
                .productId("PROD-" + i)
                .quantity(i * 10)
                .build();
            inventoryRepository.save(inventory);
        }

        mockMvc.perform(get("/api/inventory?page=0&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(0))
            .andExpect(jsonPath("$.isFirst").value(true))
            .andExpect(jsonPath("$.isLast").value(false))
            .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/inventory?page=1&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(1))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(false));

        mockMvc.perform(get("/api/inventory?page=2&size=10&sortBy=id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(2))
            .andExpect(jsonPath("$.isFirst").value(false))
            .andExpect(jsonPath("$.isLast").value(true))
            .andExpect(jsonPath("$.content.length()").value(10));
    }
}
