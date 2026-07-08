package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class PaginatedOrderResponse {
    private List<OrderResponse> content;
    private PaginationMetadata pagination;

    @Data
    public static class PaginationMetadata {
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private int pageSize;
    }
}
