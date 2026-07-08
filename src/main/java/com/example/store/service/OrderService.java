package com.example.store.service;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;
import com.example.store.exception.ResourceNotFoundException;
import com.example.store.mapper.OrderMapper;
import com.example.store.repository.CustomerRepository;
import com.example.store.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    public List<OrderResponse> getAllOrders() {
        log.info("Fetching all orders");
        List<OrderResponse> orders = orderMapper.ordersToOrderResponseList(orderRepository.findAllWithCustomer());
        log.info("Retrieved {} orders", orders.size());
        return orders;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer id: {}", request.getCustomerId());
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> {
                    log.warn("Customer not found with id: {}", request.getCustomerId());
                    return new ResourceNotFoundException("Customer", request.getCustomerId());
                });

        Order order = orderMapper.createOrderRequestToOrder(request);
        order.setCustomer(customer);
        OrderResponse response = orderMapper.orderToOrderResponse(orderRepository.save(order));
        log.info("Order created with id: {} for customer: {}", response.getId(), customer.getName());
        return response;
    }
}
