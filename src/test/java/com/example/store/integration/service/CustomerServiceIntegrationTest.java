package com.example.store.integration.service;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerResponse;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.CustomerRepository;
import com.example.store.service.CustomerService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link CustomerService} verifying transaction behavior and constraint enforcement against a
 * real PostgreSQL instance.
 */
@SpringBootTest
@Transactional
class CustomerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void createCustomer_withDuplicateEmail_shouldThrowAndLeaveCountUnchanged() {
        // Given: an existing customer with a known email
        customerRepository.save(TestDataFactory.createCustomer("Alice", "alice@example.com"));
        long countBefore = customerRepository.count();

        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setName("Alice Duplicate");
        request.setEmail("alice@example.com");

        // When / Then: creating a customer with the same email throws
        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice@example.com");

        // And: the customer count remains unchanged
        assertThat(customerRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void createCustomer_withValidInput_shouldPersistCustomerCorrectly() {
        // Given
        long countBefore = customerRepository.count();

        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setName("Bob");
        request.setEmail("bob@example.com");

        // When
        CustomerResponse response = customerService.createCustomer(request);

        // Then: response contains correct data with generated ID
        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Bob");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");

        // And: customer is actually persisted in the database
        assertThat(customerRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(customerRepository.count()).isEqualTo(countBefore + 1);
    }
}
