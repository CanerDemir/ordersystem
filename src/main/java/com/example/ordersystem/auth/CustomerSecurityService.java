package com.example.ordersystem.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("customerSecurityService")
public class CustomerSecurityService {

    public boolean hasCustomerAccess(Long customerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // ADMIN rolüne sahipse tüm customer verilerine erişebilir
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        // CUSTOMER rolünde ise kendi ID'si ile eşleşmelidir
        if (isCustomer && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getCustomerId().equals(customerId);
        }

        return false;
    }
}