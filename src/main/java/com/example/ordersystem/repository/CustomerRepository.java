package com.example.ordersystem.repository;

import com.example.ordersystem.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT DISTINCT c FROM Customer c " +
            "LEFT JOIN FETCH c.customerRoles cr " +
            "LEFT JOIN FETCH cr.role " +
            "WHERE c.email = :email")
    Optional<Customer> findByEmailWithRoles(@Param("email")  String email);
}
