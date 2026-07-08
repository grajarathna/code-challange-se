package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "Order description is required")
    @Size(max = 255, message = "Order description must not exceed 255 characters")
    private String description;

    @NotNull(message = "Customer ID is required") private Long customerId;
}
