package com.example.store.integration.repository;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.OrderRepository;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByIdWithCustomer_shouldEagerlyLoadCustomerAndProducts() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Alice", "alice-order-repo@test.com");
        entityManager.persist(customer);

        Product product1 = TestDataFactory.createProduct("Widget");
        Product product2 = TestDataFactory.createProduct("Gadget");
        entityManager.persist(product1);
        entityManager.persist(product2);

        Order order = TestDataFactory.createOrder("Test Order", customer, List.of(product1, product2));
        entityManager.persist(order);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Order> result = orderRepository.findByIdWithCustomer(order.getId());

        // Then
        assertThat(result).isPresent();
        Order loaded = result.get();
        assertThat(Hibernate.isInitialized(loaded.getCustomer())).isTrue();
        assertThat(loaded.getCustomer().getName()).isEqualTo("Alice");
        assertThat(Hibernate.isInitialized(loaded.getProducts())).isTrue();
        assertThat(loaded.getProducts()).hasSize(2);
    }

    @Test
    void findByIdWithCustomer_withNonExistentId_shouldReturnEmpty() {
        // When
        Optional<Order> result = orderRepository.findByIdWithCustomer(999999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findOrderIds_shouldReturnCorrectPageOfIds() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Bob", "bob-order-repo@test.com");
        entityManager.persist(customer);

        Product product = TestDataFactory.createProduct("Item");
        entityManager.persist(product);

        for (int i = 0; i < 5; i++) {
            Order order = TestDataFactory.createOrder("Order " + i, customer, List.of(product));
            entityManager.persist(order);
        }
        entityManager.flush();
        entityManager.clear();

        // When
        Page<Long> page = orderRepository.findOrderIds(PageRequest.of(0, 3));

        // Then
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page.getContent()).doesNotHaveDuplicates();
    }

    @Test
    void findOrderIds_shouldRespectPageSize() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Charlie", "charlie-order-repo@test.com");
        entityManager.persist(customer);

        Product product = TestDataFactory.createProduct("Thing");
        entityManager.persist(product);

        for (int i = 0; i < 7; i++) {
            Order order = TestDataFactory.createOrder("Order " + i, customer, List.of(product));
            entityManager.persist(order);
        }
        entityManager.flush();
        entityManager.clear();

        // When
        Page<Long> page1 = orderRepository.findOrderIds(PageRequest.of(0, 4));
        Page<Long> page2 = orderRepository.findOrderIds(PageRequest.of(1, 4));

        // Then
        assertThat(page1.getContent()).hasSize(4);
        assertThat(page2.getContent().size()).isLessThanOrEqualTo(4);
        // Ensure no overlap between pages
        assertThat(page1.getContent()).doesNotContainAnyElementsOf(page2.getContent());
    }

    @Test
    void findAllWithCustomerAndProductsByIds_shouldReturnDistinctOrdersWithRelationships() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Diana", "diana-order-repo@test.com");
        entityManager.persist(customer);

        Product product1 = TestDataFactory.createProduct("Alpha");
        Product product2 = TestDataFactory.createProduct("Beta");
        Product product3 = TestDataFactory.createProduct("Gamma");
        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.persist(product3);

        Order order1 = TestDataFactory.createOrder("Order A", customer, List.of(product1, product2));
        Order order2 = TestDataFactory.createOrder("Order B", customer, List.of(product2, product3));
        entityManager.persist(order1);
        entityManager.persist(order2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<Order> results =
                orderRepository.findAllWithCustomerAndProductsByIds(List.of(order1.getId(), order2.getId()));

        // Then — no duplicates from JOIN
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Order::getId).doesNotHaveDuplicates();

        // Verify all relationships are initialized
        for (Order order : results) {
            assertThat(Hibernate.isInitialized(order.getCustomer())).isTrue();
            assertThat(Hibernate.isInitialized(order.getProducts())).isTrue();
        }

        // Verify correct product counts
        Order loadedOrder1 = results.stream()
                .filter(o -> o.getId().equals(order1.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(loadedOrder1.getProducts()).hasSize(2);

        Order loadedOrder2 = results.stream()
                .filter(o -> o.getId().equals(order2.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(loadedOrder2.getProducts()).hasSize(2);
    }

    @Test
    void findAllWithCustomerAndProductsByIds_shouldHandleMissingIds() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Eve", "eve-order-repo@test.com");
        entityManager.persist(customer);

        Product product = TestDataFactory.createProduct("Delta");
        entityManager.persist(product);

        Order order = TestDataFactory.createOrder("Existing Order", customer, List.of(product));
        entityManager.persist(order);
        entityManager.flush();
        entityManager.clear();

        // When — include a non-existent ID
        List<Order> results = orderRepository.findAllWithCustomerAndProductsByIds(List.of(order.getId(), 999999L));

        // Then — missing IDs are silently excluded
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(order.getId());
    }
}
