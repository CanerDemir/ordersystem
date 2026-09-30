package com.example.ordersystem.repository;

import com.example.ordersystem.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // A: Customer'a ait cart'ı lazy ilişkileriyle getiren derived query
    Optional<Cart> findByCustomer_Id(Long customerId);

    // B: Authorization / Ownership kontrolü için derived query
    Optional<Cart> findByIdAndCustomer_Id(Long id, Long customerId);

    // C: DTO Mapping ve Cart Detail response için N+1'i engelleyen tek JPQL fetch join sorgusu
    @Query("SELECT DISTINCT c FROM Cart c " +
            "LEFT JOIN FETCH c.items i " +
            "LEFT JOIN FETCH i.product " +
            "WHERE c.customer.id = :customerId")
    Optional<Cart> findByCustomerIdWithItemsAndProducts(@Param("customerId") Long customerId);
}
