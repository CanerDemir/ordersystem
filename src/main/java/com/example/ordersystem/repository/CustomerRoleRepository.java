package com.example.ordersystem.repository;

import com.example.ordersystem.entity.CustomerRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRoleRepository extends JpaRepository<CustomerRole, Long> {
    List<CustomerRole> findByCustomerId(Long customerId);
}
