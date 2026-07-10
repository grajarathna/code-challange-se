package com.example.store.controller;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerResponse;
import com.example.store.service.ApiKeyService;
import com.example.store.service.CustomerService;
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

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean // Required for Spring context — ApiKeyAuthFilter depends on it (filters disabled via addFilters=false)
    private ApiKeyService apiKeyService;

    private CustomerResponse customerResponse;
    private CreateCustomerRequest createRequest;

    @BeforeEach
    void setUp() {
        customerResponse = new CustomerResponse();
        customerResponse.setId(1L);
        customerResponse.setName("John Doe");
        customerResponse.setEmail("john.doe@example.com");

        createRequest = new CreateCustomerRequest();
        createRequest.setName("John Doe");
        createRequest.setEmail("john.doe@example.com");
    }

    @Test
    void testGetAllCustomers() throws Exception {
        CustomerResponse secondCustomer = new CustomerResponse();
        secondCustomer.setId(2L);
        secondCustomer.setName("Jane Smith");

        when(customerService.getAllCustomers(null)).thenReturn(List.of(customerResponse, secondCustomer));

        mockMvc.perform(get("/api/v1/customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Jane Smith"));
    }

    @Test
    void testCreateCustomer() throws Exception {
        when(customerService.createCustomer(any(CreateCustomerRequest.class))).thenReturn(customerResponse);

        mockMvc.perform(post("/api/v1/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void testCreateCustomer_ValidationFails_BlankName() throws Exception {
        CreateCustomerRequest invalidRequest = new CreateCustomerRequest();

        mockMvc.perform(post("/api/v1/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateCustomer_DuplicateEmail_ReturnsConflict() throws Exception {
        when(customerService.createCustomer(any(CreateCustomerRequest.class)))
                .thenThrow(new IllegalArgumentException("Customer with email 'john.doe@example.com' already exists"));

        mockMvc.perform(post("/api/v1/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Customer with email 'john.doe@example.com' already exists"));
    }

    @Test
    void testSearchCustomersByName() throws Exception {
        when(customerService.getAllCustomers("john")).thenReturn(List.of(customerResponse));

        mockMvc.perform(get("/api/v1/customer").param("name", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }

    @Test
    void testSearchCustomersByName_NoResults() throws Exception {
        when(customerService.getAllCustomers("zzz")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customer").param("name", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testSearchCustomersByName_BlankParam_ReturnsAll() throws Exception {
        when(customerService.getAllCustomers("")).thenReturn(List.of(customerResponse));

        mockMvc.perform(get("/api/v1/customer").param("name", "")).andExpect(status().isOk());
    }
}
