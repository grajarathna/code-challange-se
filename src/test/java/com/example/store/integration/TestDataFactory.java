package com.example.store.integration;

import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import java.util.List;

/**
 * Utility class providing static factory methods to create test entity instances with all required (NOT NULL) fields
 * populated and valid relationships.
 */
public class TestDataFactory {

    private TestDataFactory() {
        // Utility class — prevent instantiation
    }

    /**
     * Creates a Customer entity with the given name and email.
     *
     * @param name the customer name (NOT NULL)
     * @param email the customer email (NOT NULL, UNIQUE)
     * @return a Customer entity ready for persistence
     */
    public static Customer createCustomer(String name, String email) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        return customer;
    }

    /**
     * Creates a Product entity with the given description.
     *
     * @param description the product description (NOT NULL)
     * @return a Product entity ready for persistence
     */
    public static Product createProduct(String description) {
        Product product = new Product();
        product.setDescription(description);
        return product;
    }

    /**
     * Creates an Order entity with valid relationships to a Customer and Products.
     *
     * @param description the order description (NOT NULL)
     * @param customer the associated customer (NOT NULL, must satisfy FK constraint)
     * @param products the associated products (satisfies order_product FK constraints)
     * @return an Order entity ready for persistence
     */
    public static Order createOrder(String description, Customer customer, List<Product> products) {
        Order order = new Order();
        order.setDescription(description);
        order.setCustomer(customer);
        order.setProducts(products);
        return order;
    }
}
