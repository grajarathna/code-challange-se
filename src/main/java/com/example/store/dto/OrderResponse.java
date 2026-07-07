package com.example.store.dto;

import lombok.Data;

@Data
public class OrderResponse {
    private Long id;
    private String description;
    private OrderCustomerDTO customer;
}
