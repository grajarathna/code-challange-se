package com.example.store.service;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderResponse;
import com.example.store.dto.ProductResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.mapper.OrderMapper;
import com.example.store.mapper.ProductMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;
import com.example.store.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceCacheTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private ProductMapper productMapper;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void getOrderById_cacheHit_repositoryCalledOnlyOnce() {
        Order order = new Order();
        order.setId(1L);
        order.setDescription("Test Order");
        order.setCustomer(new Customer());
        order.setProducts(new ArrayList<>());

        OrderCustomerDTO customerDTO = new OrderCustomerDTO();
        customerDTO.setId(1L);
        customerDTO.setName("John");

        OrderResponse response = new OrderResponse();
        response.setId(1L);
        response.setDescription("Test Order");
        response.setCustomer(customerDTO);
        response.setProducts(List.of());

        when(orderRepository.findByIdWithCustomer(1L)).thenReturn(Optional.of(order));
        when(orderMapper.orderToOrderResponse(order)).thenReturn(response);

        OrderResponse first = orderService.getOrderById(1L);
        OrderResponse second = orderService.getOrderById(1L);

        assertThat(first).isEqualTo(second);
        verify(orderRepository, times(1)).findByIdWithCustomer(1L);
    }

    @Test
    void createOrder_evictsProductCaches() {
        // Set up product data for warming product cache
        Product product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
        product.setOrders(new ArrayList<>());

        ProductResponse productResponse = new ProductResponse();
        productResponse.setId(1L);
        productResponse.setDescription("Widget");
        productResponse.setOrderIds(List.of());

        when(productRepository.findAllWithOrders()).thenReturn(List.of(product));
        when(productMapper.productsToProductResponseList(any())).thenReturn(List.of(productResponse));

        // Warm product cache
        productService.getAllProducts();

        // Set up order creation mocks
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("John");

        Order order = new Order();
        order.setId(1L);
        order.setDescription("New Order");
        order.setCustomer(customer);
        order.setProducts(new ArrayList<>());

        OrderCustomerDTO customerDTO = new OrderCustomerDTO();
        customerDTO.setId(1L);
        customerDTO.setName("John");

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(1L);
        orderResponse.setDescription("New Order");
        orderResponse.setCustomer(customerDTO);
        orderResponse.setProducts(List.of());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setDescription("New Order");
        request.setCustomerId(1L);
        request.setProductIds(List.of(1L));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findAllById(List.of(1L))).thenReturn(List.of(product));
        when(orderMapper.createOrderRequestToOrder(request)).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.orderToOrderResponse(order)).thenReturn(orderResponse);

        // Create order should evict product caches
        orderService.createOrder(request);

        // getAllProducts should hit repository again
        productService.getAllProducts();

        verify(productRepository, times(2)).findAllWithOrders();
    }

    @Test
    void getOrderById_resourceNotFoundExceptionIsNotCached() {
        when(orderRepository.findByIdWithCustomer(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(999L)).isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> orderService.getOrderById(999L)).isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, times(2)).findByIdWithCustomer(999L);
    }
}
