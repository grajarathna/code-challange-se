package com.example.store.repository;

import com.example.store.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.orders")
    List<Product> findAllWithOrders();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.orders WHERE p.id = :id")
    Optional<Product> findByIdWithOrder(@Param("id") Long id);
}
