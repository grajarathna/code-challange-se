package com.example.store.integration.repository;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.CustomerRepository;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByNameContainingIgnoreCase_shouldMatchCaseInsensitively() {
        // Given
        Customer alice = TestDataFactory.createCustomer("Alice Johnson", "alice@test.com");
        entityManager.persist(alice);

        Customer bob = TestDataFactory.createCustomer("Bob ALICE Smith", "bob@test.com");
        entityManager.persist(bob);

        Customer charlie = TestDataFactory.createCustomer("Charlie Brown", "charlie@test.com");
        entityManager.persist(charlie);

        entityManager.flush();
        entityManager.clear();

        // When — search with different case
        List<Customer> results = customerRepository.findByNameContainingIgnoreCase("alice");

        // Then — should find both Alice and Bob (whose name contains "ALICE")
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Customer::getEmail).containsExactlyInAnyOrder("alice@test.com", "bob@test.com");
    }

    @Test
    void findByNameContainingIgnoreCase_shouldEagerlyLoadOrders() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Jane Doe", "jane@test.com");
        entityManager.persist(customer);

        Product product = TestDataFactory.createProduct("Widget");
        entityManager.persist(product);

        Order order = TestDataFactory.createOrder("Jane's Order", customer, List.of(product));
        entityManager.persist(order);

        entityManager.flush();
        entityManager.clear();

        // When
        List<Customer> results = customerRepository.findByNameContainingIgnoreCase("jane");

        // Then — orders should be eagerly loaded (JOIN FETCH in the query)
        assertThat(results).hasSize(1);
        Customer loaded = results.get(0);
        assertThat(Hibernate.isInitialized(loaded.getOrders())).isTrue();
        assertThat(loaded.getOrders()).hasSize(1);
        assertThat(loaded.getOrders().get(0).getDescription()).isEqualTo("Jane's Order");
    }

    @Test
    void findByNameContainingIgnoreCase_shouldReturnEmptyListWhenNoMatch() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Alice", "alice@test.com");
        entityManager.persist(customer);

        entityManager.flush();
        entityManager.clear();

        // When
        List<Customer> results = customerRepository.findByNameContainingIgnoreCase("zzz");

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void findByEmail_shouldReturnCorrectCustomer() {
        // Given
        Customer alice = TestDataFactory.createCustomer("Alice", "alice@test.com");
        entityManager.persist(alice);

        Customer bob = TestDataFactory.createCustomer("Bob", "bob@test.com");
        entityManager.persist(bob);

        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Customer> result = customerRepository.findByEmail("alice@test.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
        assertThat(result.get().getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void findByEmail_shouldReturnEmptyWhenNotFound() {
        // Given
        Customer alice = TestDataFactory.createCustomer("Alice", "alice@test.com");
        entityManager.persist(alice);

        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Customer> result = customerRepository.findByEmail("nonexistent@test.com");

        // Then
        assertThat(result).isEmpty();
    }
}
