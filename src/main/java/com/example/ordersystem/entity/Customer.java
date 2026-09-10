package com.example.ordersystem.entity;

import com.example.ordersystem.exception.CustomerMustHaveRoleException;
import jakarta.persistence.*;
import lombok.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;

@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class Customer {
    @Id
    @SequenceGenerator(
            name = "customer_seq",
            sequenceName = "customer_seq",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    private Long id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false,  length = 100)
    private String lastName;

    @Column(unique = true, nullable = false,  length = 255)
    private String email;

    @Column(nullable = false,  length = 100)
    private String phone;

    @Column(nullable = false,  length = 255)
    private String password;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,  fetch = FetchType.LAZY)
    private Set<CustomerRole> customerRoles = new HashSet<>();

    public Customer(String firstName, String lastName, String email,  String phone,  String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public void assignRole(Role role) {
        if (role == null) {
            throw new NullPointerException("Role cannot be null");
        }

        boolean exists = hasRole(role);

        if (!exists) {
            CustomerRole customerRole = new CustomerRole(this, role);
            this.customerRoles.add(customerRole);
        }
    }

    public void removeRole(Role role) {
        if (role == null) {
            return;
        }
        if (this.customerRoles.size() <= 1 && hasRole(role)) {
            throw new CustomerMustHaveRoleException("A customer must have at least one role. Cannot remove the last remaining role.");
        }
        this.customerRoles.removeIf(cr -> cr.getRole().equals(role));
    }

    public boolean hasRole(Role role) {
        return this.customerRoles.stream()
                .anyMatch(cr -> cr.getRole().equals(role));
    }

    public Set<CustomerRole> getCustomerRoles() {
        return Collections.unmodifiableSet(customerRoles);
    }
}
