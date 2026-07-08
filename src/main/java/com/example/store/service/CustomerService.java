package com.example.store.service;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerResponse;
import com.example.store.entity.Customer;
import com.example.store.mapper.CustomerMapper;
import com.example.store.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public List<CustomerResponse> getAllCustomers() {
        log.info("Fetching all customers");
        List<CustomerResponse> customers = customerMapper.customersToCustomerResponseList(customerRepository.findAllWithOrders());
        log.info("Retrieved {} customers", customers.size());
        return customers;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer with name: {}", request.getName());
        Customer customer = customerMapper.createCustomerRequestToCustomer(request);
        CustomerResponse response = customerMapper.customerToCustomerResponse(customerRepository.save(customer));
        log.info("Customer created with id: {}", response.getId());
        return response;
    }
}
