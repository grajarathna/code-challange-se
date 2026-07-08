package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "Order description is required")
    @Size(max = 255, message = "Order description must not exceed 255 characters")
    private String description;

    @NotNull(message = "Customer ID is required") private Long customerId;

    @NotEmpty(message = "At least one product ID is required")
    private List<@NotNull(message = "Product ID cannot be null") Long> productIds;
}
