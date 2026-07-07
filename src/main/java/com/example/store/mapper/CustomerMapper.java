package com.example.store.mapper;

import com.example.store.dto.CreateCustomerRequest;
import com.example.store.dto.CustomerResponse;
import com.example.store.entity.Customer;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse customerToCustomerResponse(Customer customer);

    List<CustomerResponse> customersToCustomerResponseList(List<Customer> customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orders", ignore = true)
    Customer createCustomerRequestToCustomer(CreateCustomerRequest request);
}
