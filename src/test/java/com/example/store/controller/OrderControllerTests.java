package com.example.store.controller;

import com.example.store.dto.CheckoutRequest;
import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderResponse;
import com.example.store.dto.PaginatedOrderResponse;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse orderResponse;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        OrderCustomerDTO customerDTO = new OrderCustomerDTO();
        customerDTO.setId(1L);
        customerDTO.setName("John Doe");

        orderResponse = new OrderResponse();
        orderResponse.setId(1L);
        orderResponse.setDescription("Test Order");
        orderResponse.setCustomer(customerDTO);

        createRequest = new CreateOrderRequest();
        createRequest.setDescription("Test Order");
        createRequest.setCustomerId(1L);
        createRequest.setProductIds(List.of(1L));
    }

    // @Test
    // void testGetAllOrders() throws Exception {
    //     when(orderService.getAllOrders()).thenReturn(List.of(orderResponse));
    //
    //     mockMvc.perform(get("/api/v1/order"))
    //             .andExpect(status().isOk())
    //             .andExpect(jsonPath("$[0].id").value(1))
    //             .andExpect(jsonPath("$[0].description").value("Test Order"))
    //             .andExpect(jsonPath("$[0].customer.name").value("John Doe"));
    // }

    @Test
    void testGetAllOrders_DefaultPagination() throws Exception {
        PaginatedOrderResponse paginatedResponse = buildPaginatedResponse(List.of(orderResponse), 0, 20, 1, 1);
        when(orderService.getAllOrders(0, 20)).thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Test Order"))
                .andExpect(jsonPath("$.content[0].customer.name").value("John Doe"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.pagination.currentPage").value(0))
                .andExpect(jsonPath("$.pagination.pageSize").value(20));
    }

    @Test
    void testGetAllOrders_WithExplicitPageAndSize() throws Exception {
        PaginatedOrderResponse paginatedResponse = buildPaginatedResponse(List.of(orderResponse), 2, 10, 50, 5);
        when(orderService.getAllOrders(2, 10)).thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/order").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.pagination.currentPage").value(2))
                .andExpect(jsonPath("$.pagination.pageSize").value(10))
                .andExpect(jsonPath("$.pagination.totalElements").value(50))
                .andExpect(jsonPath("$.pagination.totalPages").value(5));
    }

    @Test
    void testGetAllOrders_EmptyPage() throws Exception {
        PaginatedOrderResponse paginatedResponse = buildPaginatedResponse(Collections.emptyList(), 5, 20, 50, 3);
        when(orderService.getAllOrders(5, 20)).thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/order").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pagination.currentPage").value(5))
                .andExpect(jsonPath("$.pagination.totalElements").value(50));
    }

    private PaginatedOrderResponse buildPaginatedResponse(
            List<OrderResponse> content, int page, int size, long totalElements, int totalPages) {
        PaginatedOrderResponse response = new PaginatedOrderResponse();
        response.setContent(content);
        PaginatedOrderResponse.PaginationMetadata metadata = new PaginatedOrderResponse.PaginationMetadata();
        metadata.setTotalElements(totalElements);
        metadata.setTotalPages(totalPages);
        metadata.setCurrentPage(page);
        metadata.setPageSize(size);
        response.setPagination(metadata);
        return response;
    }

    @Test
    void testCreateOrder() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/v1/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.name").value("John Doe"));
    }

    @Test
    void testCreateOrder_ValidationFails_MissingFields() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest();

        mockMvc.perform(post("/api/v1/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateOrder_CustomerNotFound() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class)))
                .thenThrow(new ResourceNotFoundException("Customer", 999L));

        mockMvc.perform(post("/api/v1/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetOrderById() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.name").value("John Doe"));
    }

    @Test
    void testGetOrderById_NotFound() throws Exception {
        when(orderService.getOrderById(999L)).thenThrow(new ResourceNotFoundException("Order", 999L));

        mockMvc.perform(get("/api/v1/order/999")).andExpect(status().isNotFound());
    }

    @Test
    void testCheckout_NewCustomer() throws Exception {
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setDescription("Checkout Order");
        checkoutRequest.setProductIds(List.of(1L, 2L));
        CheckoutRequest.CustomerInfo customerInfo = new CheckoutRequest.CustomerInfo();
        customerInfo.setName("Jane Doe");
        customerInfo.setEmail("jane@example.com");
        checkoutRequest.setCustomer(customerInfo);

        when(orderService.checkout(any(CheckoutRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/v1/order/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Test Order"))
                .andExpect(jsonPath("$.customer.name").value("John Doe"));
    }

    @Test
    void testCheckout_ValidationFails_MissingCustomer() throws Exception {
        CheckoutRequest invalidRequest = new CheckoutRequest();

        mockMvc.perform(post("/api/v1/order/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCheckout_ProductNotFound() throws Exception {
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setDescription("Checkout Order");
        checkoutRequest.setProductIds(List.of(1L, 9999L));
        CheckoutRequest.CustomerInfo customerInfo = new CheckoutRequest.CustomerInfo();
        customerInfo.setName("Jane Doe");
        customerInfo.setEmail("jane@example.com");
        checkoutRequest.setCustomer(customerInfo);

        when(orderService.checkout(any(CheckoutRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product", 9999L));

        mockMvc.perform(post("/api/v1/order/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isNotFound());
    }
}
