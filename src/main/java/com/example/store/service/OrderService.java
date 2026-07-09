package com.example.store.service;

import com.example.store.dto.CheckoutRequest;
import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderResponse;
import com.example.store.dto.PaginatedOrderResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final CacheManager cacheManager;

    /**
     * Retrieves all orders with their associated customer data.
     *
     * @return list of all orders
     */
    @Deprecated
    public List<OrderResponse> getAllOrders() {
        log.info("Fetching all orders");
        List<OrderResponse> orders = orderMapper.ordersToOrderResponseList(orderRepository.findAllWithCustomer());
        log.info("Retrieved {} orders", orders.size());
        return orders;
    }

    /**
     * Retrieves a paginated list of orders with their associated customer and products data. Uses a two-query ID-based
     * strategy to minimize database round trips.
     *
     * @param page the zero-based page number
     * @param size the requested page size (capped at 100)
     * @return paginated response containing orders and pagination metadata
     */
    public PaginatedOrderResponse getAllOrders(int page, int size) {
        // input sanitization if inputs are negative
        int validPage = Math.max(page, 0);
        int validSize = size <= 0 ? 20 : Math.min(size, 100);
        log.info("Fetching paginated orders: page={}, size={} (effective={})", page, size, validSize);

        // Query 1: Get page of IDs + total count (lightweight, pagination at DB level, ordered by ID)
        Page<Long> idPage = orderRepository.findOrderIds(PageRequest.of(validPage, validSize));

        if (idPage.isEmpty()) {
            log.info("No orders found for page={}, returning empty response", validPage);
            return buildEmptyPaginatedResponse(validPage, validSize, idPage.getTotalElements(), idPage.getTotalPages());
        }

        // Query 2: Full fetch with JOIN FETCH scoped to this page's IDs
        List<Order> orders = orderRepository.findAllWithCustomerAndProductsByIds(idPage.getContent());

        // Maintain page order (sort by ID to match the paginated ID query order)
        orders.sort(Comparator.comparing(Order::getId));

        List<OrderResponse> content = orderMapper.ordersToOrderResponseList(orders);
        log.info("Retrieved {} orders for page={}", content.size(), validPage);

        return buildPaginatedResponse(content, validPage, validSize, idPage.getTotalElements(), idPage.getTotalPages());
    }

    private PaginatedOrderResponse buildEmptyPaginatedResponse(
            int page, int pageSize, long totalElements, int totalPages) {
        PaginatedOrderResponse response = new PaginatedOrderResponse();
        response.setContent(Collections.emptyList());

        PaginatedOrderResponse.PaginationMetadata metadata = new PaginatedOrderResponse.PaginationMetadata();
        metadata.setTotalElements(totalElements);
        metadata.setTotalPages(totalPages);
        metadata.setCurrentPage(page);
        metadata.setPageSize(pageSize);
        response.setPagination(metadata);

        return response;
    }

    private PaginatedOrderResponse buildPaginatedResponse(
            List<OrderResponse> content, int page, int pageSize, long totalElements, int totalPages) {
        PaginatedOrderResponse response = new PaginatedOrderResponse();
        response.setContent(content);

        PaginatedOrderResponse.PaginationMetadata metadata = new PaginatedOrderResponse.PaginationMetadata();
        metadata.setTotalElements(totalElements);
        metadata.setTotalPages(totalPages);
        metadata.setCurrentPage(page);
        metadata.setPageSize(pageSize);
        response.setPagination(metadata);

        return response;
    }

    /**
     * Creates a new order for an existing customer with specified products.
     *
     * @param request the order creation request containing description, customer ID, and product IDs
     * @return the created order with generated ID, associated customer, and products
     * @throws ResourceNotFoundException if the specified customer does not exist
     */
    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer id: {}", request.getCustomerId());
        Customer customer = customerRepository.findById(request.getCustomerId()).orElseThrow(() -> {
            log.warn("Customer not found with id: {}", request.getCustomerId());
            return new ResourceNotFoundException("Customer", request.getCustomerId());
        });

        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            List<Long> foundIds = products.stream().map(Product::getId).toList();
            List<Long> missingIds = request.getProductIds().stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            log.warn("Product IDs not found: {}", missingIds);
            throw new ResourceNotFoundException("Product", missingIds.get(0));
        }

        Order order = orderMapper.createOrderRequestToOrder(request);
        order.setCustomer(customer);
        order.setProducts(products);
        OrderResponse response = orderMapper.orderToOrderResponse(orderRepository.save(order));

        // Targeted eviction: only evict productById entries for products in this order
        request.getProductIds()
                .forEach(productId -> cacheManager.getCache("productById").evict(productId));

        log.info("Order created with id: {} for customer: {}", response.getId(), customer.getName());
        return response;
    }

    /**
     * Retrieves a specific order by its ID with associated customer data.
     *
     * @param orderId the ID of the order to retrieve
     * @return the order details
     * @throws ResourceNotFoundException if the order does not exist
     */
    @Cacheable(cacheNames = "orderById", key = "#orderId")
    public OrderResponse getOrderById(Long orderId) {
        log.info("Fetching order with id: {}", orderId);
        return orderMapper.orderToOrderResponse(
                orderRepository.findByIdWithCustomer(orderId).orElseThrow(() -> {
                    log.warn("Order not found with id: {}", orderId);
                    return new ResourceNotFoundException("Order", orderId);
                }));
    }

    /**
     * Performs a checkout operation: resolves customer by email (creates if not found), validates products, and creates
     * an order in a single transaction.
     *
     * @param request the checkout request containing order description, product IDs, and inline customer data
     * @return the created order with generated ID, associated customer, and products
     * @throws ResourceNotFoundException if any specified product does not exist
     */
    @Transactional
    @CacheEvict(cacheNames = "products", allEntries = true)
    public OrderResponse checkout(CheckoutRequest request) {
        log.info(
                "Processing checkout for customer email: {}",
                request.getCustomer().getEmail());

        // Step 1: Resolve customer by email (find or create)
        Customer customer = customerRepository
                .findByEmail(request.getCustomer().getEmail())
                .orElseGet(() -> {
                    Customer newCustomer = new Customer();
                    newCustomer.setName(request.getCustomer().getName());
                    newCustomer.setEmail(request.getCustomer().getEmail());
                    return customerRepository.save(newCustomer);
                });

        // Step 2: Validate products (same as createOrder)
        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            List<Long> foundIds = products.stream().map(Product::getId).toList();
            List<Long> missingIds = request.getProductIds().stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            log.warn("Product IDs not found during checkout: {}", missingIds);
            throw new ResourceNotFoundException("Product", missingIds.get(0));
        }

        // Step 3: Create and save order
        Order order = new Order();
        order.setDescription(request.getDescription());
        order.setCustomer(customer);
        order.setProducts(products);
        OrderResponse response = orderMapper.orderToOrderResponse(orderRepository.save(order));

        // Step 4: Targeted cache eviction
        request.getProductIds()
                .forEach(productId -> cacheManager.getCache("productById").evict(productId));

        log.info("Checkout completed: order id={} for customer: {}", response.getId(), customer.getName());
        return response;
    }
}
