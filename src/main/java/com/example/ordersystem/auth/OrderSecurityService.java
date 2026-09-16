package com.example.ordersystem.auth;

import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("orderSecurity")
@RequiredArgsConstructor
public class OrderSecurityService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public boolean isOrderOwner(Long orderId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return false;
        }

        // Customer'ın söz konusu orderId'nin sahibi olup olmadığını DB üzerinden hızlıca sorgular
        return orderRepository.existsByIdAndCustomerId(orderId, userDetails.getCustomerId());
    }
}