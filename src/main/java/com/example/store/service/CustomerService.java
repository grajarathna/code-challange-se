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

    /**
     * Retrieves customers, optionally filtered by name substring.
     *
     * @param name optional search term to match against customer names (case-insensitive)
     * @return all customers if name is null/blank, or matching customers otherwise
     */
    public List<CustomerResponse> getAllCustomers(String name) {
        return (name != null && !name.isBlank()) ? findByNameContainingIgnoreCase(name) : getAllCustomers();
    }

    /**
     * Creates a new customer.
     *
     * @param request the customer creation request containing the name
     * @return the created customer with generated ID
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.info("Creating customer with name: {}", request.getName());
        Customer customer = customerMapper.createCustomerRequestToCustomer(request);
        CustomerResponse response = customerMapper.customerToCustomerResponse(customerRepository.save(customer));
        log.info("Customer created with id: {}", response.getId());
        return response;
    }

    /**
     * Retrieves all customers with their associated orders.
     *
     * @return list of all customers
     */
    public List<CustomerResponse> getAllCustomers() {
        log.info("Fetching all customers");
        List<CustomerResponse> customers =
                customerMapper.customersToCustomerResponseList(customerRepository.findAllWithOrders());
        log.info("Retrieved {} customers", customers.size());
        return customers;
    }

    /**
     * Searches customers by a case-insensitive substring match on their name.
     *
     * @param name the search term to match
     * @return list of matching customers (may be empty)
     */
    public List<CustomerResponse> findByNameContainingIgnoreCase(String name) {
        log.info("Searching customers with name containing: {}", name);
        List<Customer> customerList = customerRepository.findByNameContainingIgnoreCase(name);
        if (!customerList.isEmpty()) {
            log.info("Found {} customers matching '{}'", customerList.size(), name);
        } else {
            log.warn("No matching customers with name containing: {}", name);
        }
        return customerMapper.customersToCustomerResponseList(customerList);
    }
}
