package com.example.store.mapper;

import com.example.store.dto.CreateOrderRequest;
import com.example.store.dto.OrderCustomerDTO;
import com.example.store.dto.OrderResponse;
import com.example.store.entity.Customer;
import com.example.store.entity.Order;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse orderToOrderResponse(Order order);

    List<OrderResponse> ordersToOrderResponseList(List<Order> orders);

    OrderCustomerDTO customerToOrderCustomerDTO(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    Order createOrderRequestToOrder(CreateOrderRequest request);
}
