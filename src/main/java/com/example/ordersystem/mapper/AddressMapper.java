package com.example.ordersystem.mapper;

import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequest request){
        return new Address(
                request.title(),
                request.city(),
                request.district(),
                request.zipCode(),
                request.country(),
                request.addressLine(),
                request.addressDetail()
        );
    }
}
