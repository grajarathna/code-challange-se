package com.example.store.service;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductResponse;
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
class ProductServiceCacheTest {

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
    void getAllProducts_cacheHit_repositoryCalledOnlyOnce() {
        Product product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
        product.setOrders(new ArrayList<>());

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setDescription("Widget");
        response.setOrderIds(List.of());

        when(productRepository.findAllWithOrders()).thenReturn(List.of(product));
        when(productMapper.productsToProductResponseList(any())).thenReturn(List.of(response));

        List<ProductResponse> first = productService.getAllProducts();
        List<ProductResponse> second = productService.getAllProducts();

        assertThat(first).isEqualTo(second);
        verify(productRepository, times(1)).findAllWithOrders();
    }

    @Test
    void getProductById_cacheHit_repositoryCalledOnlyOnce() {
        Product product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
        product.setOrders(new ArrayList<>());

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setDescription("Widget");
        response.setOrderIds(List.of());

        when(productRepository.findByIdWithOrder(1L)).thenReturn(Optional.of(product));
        when(productMapper.productToProductResponse(product)).thenReturn(response);

        ProductResponse first = productService.getProductById(1L);
        ProductResponse second = productService.getProductById(1L);

        assertThat(first).isEqualTo(second);
        verify(productRepository, times(1)).findByIdWithOrder(1L);
    }

    @Test
    void createProduct_evictsProductCaches() {
        Product product = new Product();
        product.setId(1L);
        product.setDescription("Widget");
        product.setOrders(new ArrayList<>());

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setDescription("Widget");
        response.setOrderIds(List.of());

        when(productRepository.findByIdWithOrder(1L)).thenReturn(Optional.of(product));
        when(productMapper.productToProductResponse(product)).thenReturn(response);

        // Warm cache
        productService.getProductById(1L);

        // Create product triggers eviction
        Product newProduct = new Product();
        newProduct.setId(2L);
        newProduct.setDescription("New Widget");
        newProduct.setOrders(new ArrayList<>());

        ProductResponse newResponse = new ProductResponse();
        newResponse.setId(2L);
        newResponse.setDescription("New Widget");
        newResponse.setOrderIds(List.of());

        CreateProductRequest request = new CreateProductRequest();
        request.setDescription("New Widget");

        when(productMapper.createProductRequestToProduct(request)).thenReturn(newProduct);
        when(productRepository.save(newProduct)).thenReturn(newProduct);
        when(productMapper.productToProductResponse(newProduct)).thenReturn(newResponse);

        productService.createProduct(request);

        // After eviction, getProductById should hit the repository again
        productService.getProductById(1L);

        verify(productRepository, times(2)).findByIdWithOrder(1L);
    }

    @Test
    void getProductById_resourceNotFoundExceptionIsNotCached() {
        when(productRepository.findByIdWithOrder(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L)).isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> productService.getProductById(999L)).isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, times(2)).findByIdWithOrder(999L);
    }
}
