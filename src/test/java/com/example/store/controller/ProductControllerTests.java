package com.example.store.controller;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductResponse;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.service.ApiKeyService;
import com.example.store.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean // Required for Spring context — ApiKeyAuthFilter depends on it (filters disabled via addFilters=false)
    private ApiKeyService apiKeyService;

    private ProductResponse productResponse;
    private CreateProductRequest createRequest;

    @BeforeEach
    void setUp() {
        productResponse = new ProductResponse();
        productResponse.setId(1L);
        productResponse.setDescription("Mechanical Keyboard");
        productResponse.setOrderIds(List.of(1L, 4L, 7L));

        createRequest = new CreateProductRequest();
        createRequest.setDescription("Mechanical Keyboard");
    }

    @Test
    void testGetAllProducts() throws Exception {
        ProductResponse secondProduct = new ProductResponse();
        secondProduct.setId(2L);
        secondProduct.setDescription("USB-C Cable");
        secondProduct.setOrderIds(List.of(2L));

        when(productService.getAllProducts()).thenReturn(List.of(productResponse, secondProduct));

        mockMvc.perform(get("/api/v1/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].description").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$[0].orderIds.length()").value(3))
                .andExpect(jsonPath("$[0].orderIds[0]").value(1))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].description").value("USB-C Cable"));
    }

    @Test
    void testGetProductById() throws Exception {
        when(productService.getProductById(1L)).thenReturn(productResponse);

        mockMvc.perform(get("/api/v1/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.description").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.orderIds.length()").value(3));
    }

    @Test
    void testGetProductById_NotFound() throws Exception {
        when(productService.getProductById(999L)).thenThrow(new ResourceNotFoundException("Product", 999L));

        mockMvc.perform(get("/api/v1/product/999")).andExpect(status().isNotFound());
    }

    @Test
    void testCreateProduct() throws Exception {
        ProductResponse created = new ProductResponse();
        created.setId(101L);
        created.setDescription("Mechanical Keyboard");
        created.setOrderIds(List.of());

        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.description").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.orderIds.length()").value(0));
    }

    @Test
    void testCreateProduct_ValidationFails_BlankDescription() throws Exception {
        CreateProductRequest invalidRequest = new CreateProductRequest();

        mockMvc.perform(post("/api/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
