package com.example.store.integration.service;

import com.example.store.dto.CheckoutRequest;
import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderResponse;
import com.example.store.dto.PaginatedOrderResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Product;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.integration.AbstractIntegrationTest;
import com.example.store.integration.TestDataFactory;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;
import com.example.store.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link OrderService} verifying end-to-end service flows including transaction management,
 * entity relationships, and pagination against a real PostgreSQL container.
 */
@SpringBootTest
@Transactional
class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ========== checkout() tests ==========

    @Test
    void checkout_withNewCustomerEmail_shouldCreateCustomerAndOrder() {
        // Given
        Product product = productRepository.save(TestDataFactory.createProduct("Laptop"));

        CheckoutRequest request = new CheckoutRequest();
        request.setDescription("New checkout order");
        request.setProductIds(List.of(product.getId()));
        CheckoutRequest.CustomerInfo customerInfo = new CheckoutRequest.CustomerInfo();
        customerInfo.setName("Alice");
        customerInfo.setEmail("alice@test.com");
        request.setCustomer(customerInfo);

        // When
        OrderResponse response = orderService.checkout(request);

        // Then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getDescription()).isEqualTo("New checkout order");
        assertThat(response.getCustomer().getName()).isEqualTo("Alice");
        assertThat(response.getCustomer().getEmail()).isEqualTo("alice@test.com");
        assertThat(response.getProducts()).hasSize(1);

        // Verify customer was persisted
        assertThat(customerRepository.findByEmail("alice@test.com")).isPresent();
        // Verify order was persisted
        assertThat(orderRepository.findById(response.getId())).isPresent();
    }

    @Test
    void checkout_withExistingCustomerEmail_shouldReuseCustomer() {
        // Given
        Customer existing = customerRepository.save(TestDataFactory.createCustomer("Bob", "bob@test.com"));
        Product product = productRepository.save(TestDataFactory.createProduct("Phone"));

        long customerCountBefore = customerRepository.count();

        CheckoutRequest request = new CheckoutRequest();
        request.setDescription("Repeat order");
        request.setProductIds(List.of(product.getId()));
        CheckoutRequest.CustomerInfo customerInfo = new CheckoutRequest.CustomerInfo();
        customerInfo.setName("Bob Updated");
        customerInfo.setEmail("bob@test.com"); // Same email as existing
        request.setCustomer(customerInfo);

        // When
        OrderResponse response = orderService.checkout(request);

        // Then — existing customer reused, no duplicate
        assertThat(customerRepository.count()).isEqualTo(customerCountBefore);
        assertThat(response.getCustomer().getId()).isEqualTo(existing.getId());
        assertThat(response.getCustomer().getName()).isEqualTo("Bob"); // Original name preserved
    }

    @Test
    void checkout_withNonExistentProductId_shouldThrowAndNotPersist() {
        // Given
        long orderCountBefore = orderRepository.count();

        CheckoutRequest request = new CheckoutRequest();
        request.setDescription("Should fail");
        request.setProductIds(List.of(99999L)); // Non-existent product
        CheckoutRequest.CustomerInfo customerInfo = new CheckoutRequest.CustomerInfo();
        customerInfo.setName("Charlie");
        customerInfo.setEmail("charlie-nonexistent-product@test.com");
        request.setCustomer(customerInfo);

        // When / Then — exception is thrown for non-existent product
        assertThatThrownBy(() -> orderService.checkout(request)).isInstanceOf(ResourceNotFoundException.class);

        // Verify no order was persisted (the exception stops order creation)
        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
    }

    // ========== createOrder() tests ==========

    @Test
    void createOrder_withValidInputs_shouldPersistOrderWithCorrectRelationships() {
        // Given
        Customer customer = customerRepository.save(TestDataFactory.createCustomer("Dave", "dave@test.com"));
        Product product1 = productRepository.save(TestDataFactory.createProduct("Widget"));
        Product product2 = productRepository.save(TestDataFactory.createProduct("Gadget"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Dave's order");
        request.setCustomerId(customer.getId());
        request.setProductIds(List.of(product1.getId(), product2.getId()));

        // When
        OrderResponse response = orderService.createOrder(request);

        // Then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getDescription()).isEqualTo("Dave's order");
        assertThat(response.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(response.getCustomer().getName()).isEqualTo("Dave");
        assertThat(response.getProducts()).hasSize(2);

        // Verify persisted correctly
        assertThat(orderRepository.findById(response.getId())).isPresent();
    }

    @Test
    void createOrder_withNonExistentCustomerId_shouldThrowAndNotCreateOrder() {
        // Given
        Product product = productRepository.save(TestDataFactory.createProduct("Item"));
        long orderCountBefore = orderRepository.count();

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("Should fail");
        request.setCustomerId(99999L); // Non-existent customer
        request.setProductIds(List.of(product.getId()));

        // When / Then
        assertThatThrownBy(() -> orderService.createOrder(request)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
    }

    // ========== getAllOrders(page, size) tests ==========

    @Test
    void getAllOrders_withValidParams_shouldReturnCorrectContentAndMetadata() {
        // Given — record existing count, then add 25 orders
        long existingOrderCount = orderRepository.count();
        Customer customer = customerRepository.save(TestDataFactory.createCustomer("Eve", "eve@test.com"));
        Product product = productRepository.save(TestDataFactory.createProduct("Item"));
        for (int i = 0; i < 25; i++) {
            orderRepository.save(TestDataFactory.createOrder("Order " + i, customer, List.of(product)));
        }
        long totalOrders = existingOrderCount + 25;

        // When — request page 0, size 10
        PaginatedOrderResponse response = orderService.getAllOrders(0, 10);

        // Then
        assertThat(response.getContent()).hasSize(10);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(totalOrders);
        int expectedTotalPages = (int) Math.ceil((double) totalOrders / 10);
        assertThat(response.getPagination().getTotalPages()).isEqualTo(expectedTotalPages);
        assertThat(response.getPagination().getCurrentPage()).isEqualTo(0);
        assertThat(response.getPagination().getPageSize()).isEqualTo(10);
    }

    @Test
    void getAllOrders_beyondAvailableData_shouldReturnEmptyContentWithCorrectTotals() {
        // Given — record existing count, then add 5 orders
        long existingOrderCount = orderRepository.count();
        Customer customer = customerRepository.save(TestDataFactory.createCustomer("Frank", "frank@test.com"));
        Product product = productRepository.save(TestDataFactory.createProduct("Thing"));
        for (int i = 0; i < 5; i++) {
            orderRepository.save(TestDataFactory.createOrder("Order " + i, customer, List.of(product)));
        }
        long totalOrders = existingOrderCount + 5;
        int totalPages = (int) Math.ceil((double) totalOrders / 10);

        // When — request a page far beyond available data
        int pageFarBeyond = totalPages + 10;
        PaginatedOrderResponse response = orderService.getAllOrders(pageFarBeyond, 10);

        // Then
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPagination().getTotalElements()).isEqualTo(totalOrders);
        assertThat(response.getPagination().getTotalPages()).isEqualTo(totalPages);
        assertThat(response.getPagination().getCurrentPage()).isEqualTo(pageFarBeyond);
    }

    @Test
    void getAllOrders_withNegativeAndInvalidInputs_shouldSanitize() {
        // Given — some orders
        Customer customer = customerRepository.save(TestDataFactory.createCustomer("Grace", "grace@test.com"));
        Product product = productRepository.save(TestDataFactory.createProduct("Widget"));
        for (int i = 0; i < 5; i++) {
            orderRepository.save(TestDataFactory.createOrder("Order " + i, customer, List.of(product)));
        }

        // When — negative page → treated as 0
        PaginatedOrderResponse responseNegativePage = orderService.getAllOrders(-1, 10);
        assertThat(responseNegativePage.getPagination().getCurrentPage()).isEqualTo(0);
        assertThat(responseNegativePage.getContent()).isNotEmpty();

        // When — size 0 → defaults to 20
        PaginatedOrderResponse responseZeroSize = orderService.getAllOrders(0, 0);
        assertThat(responseZeroSize.getPagination().getPageSize()).isEqualTo(20);

        // When — size negative → defaults to 20
        PaginatedOrderResponse responseNegativeSize = orderService.getAllOrders(0, -5);
        assertThat(responseNegativeSize.getPagination().getPageSize()).isEqualTo(20);

        // When — size > 100 → capped at 100
        PaginatedOrderResponse responseLargeSize = orderService.getAllOrders(0, 200);
        assertThat(responseLargeSize.getPagination().getPageSize()).isEqualTo(100);
    }

    @Test
    void getAllOrders_twoQueryStrategy_shouldReturnDistinctOrdersWithMultipleProducts() {
        // Given — orders with multiple products (this would cause duplication with naive JOIN + pagination)
        Customer customer = customerRepository.save(TestDataFactory.createCustomer("Heidi", "heidi@test.com"));
        Product product1 = productRepository.save(TestDataFactory.createProduct("Alpha"));
        Product product2 = productRepository.save(TestDataFactory.createProduct("Beta"));
        Product product3 = productRepository.save(TestDataFactory.createProduct("Gamma"));

        // Create 5 orders, each with 3 products
        for (int i = 0; i < 5; i++) {
            orderRepository.save(TestDataFactory.createOrder(
                    "Multi-product order " + i, customer, List.of(product1, product2, product3)));
        }

        long totalOrders = orderRepository.count();

        // When — request page with size 100 (max allowed)
        // Use the last page to find our newly created orders
        int totalPages = (int) Math.ceil((double) totalOrders / 100);
        PaginatedOrderResponse response = orderService.getAllOrders(totalPages - 1, 100);

        // Then — verify no duplication: totalElements should equal distinct order count
        assertThat(response.getPagination().getTotalElements()).isEqualTo(totalOrders);

        // The content should contain distinct orders only (not multiplied by product count)
        assertThat(response.getContent().size()).isLessThanOrEqualTo(100);

        // Find our multi-product orders in the response
        List<OrderResponse> multiProductOrders = response.getContent().stream()
                .filter(o -> o.getDescription().startsWith("Multi-product order"))
                .toList();

        // All 5 multi-product orders should be distinct (not 15 = 5 orders × 3 products)
        assertThat(multiProductOrders).hasSize(5);

        // Verify each order has all 3 products loaded (relationships are initialized)
        for (OrderResponse order : multiProductOrders) {
            assertThat(order.getProducts()).hasSize(3);
            assertThat(order.getCustomer()).isNotNull();
            assertThat(order.getCustomer().getName()).isEqualTo("Heidi");
        }
    }
}
