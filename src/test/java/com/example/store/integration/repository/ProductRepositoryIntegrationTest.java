package com.example.store.integration.repository;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.ProductRepository;

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
class ProductRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findAllWithOrders_shouldReturnAllProductsWithOrdersEagerlyLoaded() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Alice", "alice@product-test.com");
        entityManager.persist(customer);

        Product product1 = TestDataFactory.createProduct("Widget");
        Product product2 = TestDataFactory.createProduct("Gadget");
        Product product3 = TestDataFactory.createProduct("Standalone Product");
        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.persist(product3);

        Order order1 = TestDataFactory.createOrder("Order 1", customer, List.of(product1, product2));
        Order order2 = TestDataFactory.createOrder("Order 2", customer, List.of(product1));
        entityManager.persist(order1);
        entityManager.persist(order2);

        entityManager.flush();
        entityManager.clear();

        // When
        List<Product> products = productRepository.findAllWithOrders();

        // Then
        assertThat(products).hasSizeGreaterThanOrEqualTo(3);

        Product loadedProduct1 = products.stream()
                .filter(p -> p.getDescription().equals("Widget"))
                .findFirst()
                .orElseThrow();
        assertThat(Hibernate.isInitialized(loadedProduct1.getOrders())).isTrue();
        assertThat(loadedProduct1.getOrders()).hasSize(2);

        Product loadedProduct2 = products.stream()
                .filter(p -> p.getDescription().equals("Gadget"))
                .findFirst()
                .orElseThrow();
        assertThat(Hibernate.isInitialized(loadedProduct2.getOrders())).isTrue();
        assertThat(loadedProduct2.getOrders()).hasSize(1);

        Product loadedProduct3 = products.stream()
                .filter(p -> p.getDescription().equals("Standalone Product"))
                .findFirst()
                .orElseThrow();
        assertThat(Hibernate.isInitialized(loadedProduct3.getOrders())).isTrue();
        assertThat(loadedProduct3.getOrders()).isEmpty();
    }

    @Test
    void findByIdWithOrder_shouldReturnProductWithOrdersEagerlyLoaded() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Bob", "bob@product-test.com");
        entityManager.persist(customer);

        Product product = TestDataFactory.createProduct("Laptop");
        entityManager.persist(product);

        Order order1 = TestDataFactory.createOrder("Order A", customer, List.of(product));
        Order order2 = TestDataFactory.createOrder("Order B", customer, List.of(product));
        entityManager.persist(order1);
        entityManager.persist(order2);

        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Product> result = productRepository.findByIdWithOrder(product.getId());

        // Then
        assertThat(result).isPresent();
        Product loadedProduct = result.get();
        assertThat(loadedProduct.getDescription()).isEqualTo("Laptop");
        assertThat(Hibernate.isInitialized(loadedProduct.getOrders())).isTrue();
        assertThat(loadedProduct.getOrders()).hasSize(2);
    }

    @Test
    void findByIdWithOrder_withNonExistentId_shouldReturnEmpty() {
        // When
        Optional<Product> result = productRepository.findByIdWithOrder(999L);

        // Then
        assertThat(result).isEmpty();
    }
}
