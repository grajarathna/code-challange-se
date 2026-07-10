package com.example.store.integration.service;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;
import com.example.store.service.ProductService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ProductService} running against a real PostgreSQL container. Validates product CRUD
 * operations and order relationship loading.
 */
@SpringBootTest
@Transactional
class ProductServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void evictCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void getAllProducts_shouldReturnAllProductsMappedToResponse() {
        // Given
        long existingCount = productRepository.count();
        Product product1 = TestDataFactory.createProduct("IntTest Widget");
        Product product2 = TestDataFactory.createProduct("IntTest Gadget");
        productRepository.save(product1);
        productRepository.save(product2);
        entityManager.flush();

        // When
        List<ProductResponse> results = productService.getAllProducts();

        // Then
        assertThat(results).hasSize((int) existingCount + 2);
        assertThat(results).extracting(ProductResponse::getDescription).contains("IntTest Widget", "IntTest Gadget");
    }

    @Test
    void createProduct_shouldPersistAndReturnProductResponse() {
        // Given
        CreateProductRequest request = new CreateProductRequest();
        request.setDescription("New Laptop");

        // When
        ProductResponse response = productService.createProduct(request);

        // Then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getDescription()).isEqualTo("New Laptop");
        assertThat(response.getOrderIds()).isEmpty();

        // Verify it was actually persisted in the database
        assertThat(productRepository.findById(response.getId())).isPresent();
    }

    @Test
    void createProduct_shouldPersistMultipleProducts() {
        // Given
        long existingCount = productRepository.count();
        CreateProductRequest request1 = new CreateProductRequest();
        request1.setDescription("Product A");
        CreateProductRequest request2 = new CreateProductRequest();
        request2.setDescription("Product B");

        // When
        ProductResponse response1 = productService.createProduct(request1);
        ProductResponse response2 = productService.createProduct(request2);

        // Then
        assertThat(response1.getId()).isNotEqualTo(response2.getId());
        assertThat(productRepository.count()).isEqualTo(existingCount + 2);
    }

    @Test
    void getProductById_shouldReturnProductWithOrderRelationships() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Alice", "alice-product-svc@test.com");
        customerRepository.save(customer);

        Product product = TestDataFactory.createProduct("IntTest Headphones");
        productRepository.save(product);

        Order order1 = TestDataFactory.createOrder("Order 1", customer, List.of(product));
        Order order2 = TestDataFactory.createOrder("Order 2", customer, List.of(product));
        orderRepository.save(order1);
        orderRepository.save(order2);

        entityManager.flush();
        entityManager.clear();

        // When
        ProductResponse response = productService.getProductById(product.getId());

        // Then
        assertThat(response.getId()).isEqualTo(product.getId());
        assertThat(response.getDescription()).isEqualTo("IntTest Headphones");
        assertThat(response.getOrderIds()).hasSize(2);
        assertThat(response.getOrderIds()).contains(order1.getId(), order2.getId());
    }

    @Test
    void getProductById_withNoOrders_shouldReturnProductWithEmptyOrderIds() {
        // Given
        Product product = TestDataFactory.createProduct("Standalone Product");
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // When
        ProductResponse response = productService.getProductById(product.getId());

        // Then
        assertThat(response.getId()).isEqualTo(product.getId());
        assertThat(response.getDescription()).isEqualTo("Standalone Product");
        assertThat(response.getOrderIds()).isEmpty();
    }

    @Test
    void getProductById_withNonExistentId_shouldThrowResourceNotFoundException() {
        // Given
        Long nonExistentId = 99999L;

        // When/Then
        assertThatThrownBy(() -> productService.getProductById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    void getAllProducts_shouldReturnProductsWithOrderIdsLoaded() {
        // Given
        Customer customer = TestDataFactory.createCustomer("Bob", "bob-product-svc@test.com");
        customerRepository.save(customer);

        Product product1 = TestDataFactory.createProduct("IntTest Phone");
        Product product2 = TestDataFactory.createProduct("IntTest Tablet");
        productRepository.save(product1);
        productRepository.save(product2);

        Order order = TestDataFactory.createOrder("Mixed Order", customer, List.of(product1, product2));
        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // When
        List<ProductResponse> results = productService.getAllProducts();

        // Then
        ProductResponse phoneResponse = results.stream()
                .filter(p -> p.getDescription().equals("IntTest Phone"))
                .findFirst()
                .orElseThrow();
        ProductResponse tabletResponse = results.stream()
                .filter(p -> p.getDescription().equals("IntTest Tablet"))
                .findFirst()
                .orElseThrow();

        assertThat(phoneResponse.getOrderIds()).contains(order.getId());
        assertThat(tabletResponse.getOrderIds()).contains(order.getId());
    }
}
