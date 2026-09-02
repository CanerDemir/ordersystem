package com.example.ordersystem.repository;

import com.example.ordersystem.dto.response.OrderSummaryResponse;
import com.example.ordersystem.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.id = :orderId AND o.customer.id = :customerId
    """)
    Optional<Order> findByIdAndCustomerId(@Param("orderId") Long orderId, @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId AND o.customer.id = :customerId")
    Optional<Order> findByIdAndCustomerIdWithLock(@Param("orderId") Long orderId, @Param("customerId") Long customerId);

    @Query(value = "SELECT new com.example.ordersystem.dto.response.OrderSummaryResponse(" +
                "o.id," +
                "o.createdAt," +
                "o.status," +
                "o.totalAmount," +
                "CAST(COALESCE(SUM(i.quantity), 0) AS integer)" +
            ")" +
            "FROM Order o LEFT JOIN o.items i WHERE o.customer.id = :customerId GROUP BY o.id, o.status, o.totalAmount, o.createdAt",
            countQuery = "SELECT COUNT(o.id) FROM Order o WHERE o.customer.id = :customerId"
    )
    Page<OrderSummaryResponse> findOrderSummariesByCustomerId(@Param("customerId") Long customerId, Pageable pageable);
}
