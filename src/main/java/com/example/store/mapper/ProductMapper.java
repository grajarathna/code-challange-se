package com.example.store.mapper;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductResponse;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "orderIds", expression = "java(product.getOrders().stream().map(o -> o.getId()).toList())")
    ProductResponse productToProductResponse(Product product);

    List<ProductResponse> productsToProductResponseList(List<Product> product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orders", ignore = true)
    Product createProductRequestToProduct(CreateProductRequest request);
}
