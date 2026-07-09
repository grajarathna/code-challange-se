package com.example.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Order description is required")
    @Size(max = 255, message = "Order description must not exceed 255 characters")
    private String description;

    @NotEmpty(message = "At least one product ID is required")
    private List<@NotNull(message = "Product ID cannot be null") Long> productIds;

    @NotNull(message = "Customer information is required")
    @Valid
    private CustomerInfo customer;

    @Data
    public static class CustomerInfo {
        @NotBlank(message = "Customer name is required")
        @Size(max = 255, message = "Customer name must not exceed 255 characters")
        private String name;

        @NotBlank(message = "Customer email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Customer email must not exceed 255 characters")
        private String email;
    }
}
