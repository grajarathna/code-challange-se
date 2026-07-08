package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductResponse {
    private Long id;
    private String description;
    List<Long> orderIds; // IDs are sufficient for references.
}
