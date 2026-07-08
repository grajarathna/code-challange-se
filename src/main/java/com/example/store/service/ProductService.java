package com.example.store.service;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductResponse;
import com.example.store.entity.Product;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.mapper.ProductMapper;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /** @return all products along with the list of the order IDs which contain those products */
    @Cacheable(cacheNames = "products")
    public List<ProductResponse> getAllProducts() {
        return productMapper.productsToProductResponseList(productRepository.findAllWithOrders());
    }

    /**
     * Creates a new product.
     *
     * @param request the product creation request containing the name
     * @return the created product with generated ID
     */
    @Transactional
    @CacheEvict(
            cacheNames = {"products", "productById"},
            allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product with name: {}", request.getDescription());
        Product product = productMapper.createProductRequestToProduct(request);
        ProductResponse response = productMapper.productToProductResponse(productRepository.save(product));
        log.info("Product created with id: {}", response.getId());
        return response;
    }

    /**
     * Retrieves a specific product by its ID with associated order data.
     *
     * @param productId the ID of the product to retrieve
     * @return the product details
     * @throws ResourceNotFoundException if the order does not exist
     */
    @Cacheable(cacheNames = "productById", key = "#productId")
    public ProductResponse getProductById(Long productId) {
        log.info("Fetching product with id: {}", productId);
        return productMapper.productToProductResponse(
                productRepository.findByIdWithOrder(productId).orElseThrow(() -> {
                    log.warn("Product not found with id: {}", productId);
                    return new ResourceNotFoundException("Product", productId);
                }));
    }
}
