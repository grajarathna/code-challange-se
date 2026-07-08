package com.example.store.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Customer email must not exceed 255 characters")
    private String email;
}
