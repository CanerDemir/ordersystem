package com.example.ordersystem.controller;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.response.CustomerResponse;
import com.example.ordersystem.service.interfaces.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // CUSTOMER kendi profilini okuyabilir, ADMIN da erişebilir
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<CustomerResponse> getMyProfile(@AuthenticatedUser CurrentUser  currentUser) {
        return ResponseEntity.ok(customerService.getCustomerById(currentUser.customerId()));
    }

    // Ownership Kontrolü: CUSTOMER sadece kendi ID'sine, ADMIN ise herkese erişebilir
    @GetMapping("/{id}")
    @PreAuthorize("@customerSecurityService.hasCustomerAccess(#id)")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    // Admin-Only Operation: Tüm müşteri listesini çekme
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    // Admin-Only Operation: Müşteri silme/pasife alma
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
