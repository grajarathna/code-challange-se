package com.example.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product description is required")
    @Size(max = 255, message = "Product description must not exceed 255 characters")
    private String description;
}
