package com.ecommerce.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddCardRequest {

    // Demo/learning only - never a real PAN, and never logged anywhere in this service.
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{12,19}", message = "Card number must be 12-19 digits")
    private String cardNumber;
}
