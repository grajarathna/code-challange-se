package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomerResponse {
    private Long id;
    private String name;
    private List<CustomerOrderDTO> orders;
}
