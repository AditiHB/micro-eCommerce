package com.ecommerce.inventoryservice;

import com.ecommerce.common.exception.RequestValidationAdvice;
import com.ecommerce.inventoryservice.exception.GlobalExceptionHandler;
import com.ecommerce.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock quantities arrive as bare request parameters. A reservation of -50 used to be accepted and ADD 50
 * units; now anything that is not a positive number is a 400 and never reaches the service.
 */
@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RequestValidationAdvice.class, GlobalExceptionHandler.class})
@DisplayName("Inventory request parameter validation")
class InventoryParameterValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    @DisplayName("reserve with a negative quantity -> 400, service untouched")
    void reserveNegative() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/reserve").param("quantity", "-50"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("release with zero -> 400")
    void releaseZero() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/release").param("quantity", "0"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("update with a negative quantity -> 400")
    void updateNegative() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/1").param("quantity", "-1"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("update to zero is valid: zero is a real stock level (a sold-out item)")
    void updateToZeroIsAllowed() throws Exception {
        org.mockito.Mockito.when(inventoryService.updateInventory(1L, 0))
            .thenReturn(com.ecommerce.inventoryservice.dto.InventoryResponse.builder().id(1L).quantity(0).version(1L).build());

        mockMvc.perform(put("/api/v1/inventory/1").param("quantity", "0")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an If-Match that no longer matches is a 412 and the change is not made")
    void staleIfMatch() throws Exception {
        org.mockito.Mockito.when(inventoryService.getInventory(1L))
            .thenReturn(com.ecommerce.inventoryservice.dto.InventoryResponse.builder().id(1L).quantity(5).version(3L).build());

        mockMvc.perform(put("/api/v1/inventory/1").param("quantity", "9").header("If-Match", "\"2\""))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.errorCode").value("PRECONDITION_FAILED"));
        org.mockito.Mockito.verify(inventoryService, org.mockito.Mockito.never()).updateInventory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a missing or non-numeric quantity is also a client error, not a 500")
    void missingOrGarbage() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/1/reserve")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/inventory/1/reserve").param("quantity", "abc")).andExpect(status().is4xxClientError());
        verifyNoInteractions(inventoryService);
    }
}
