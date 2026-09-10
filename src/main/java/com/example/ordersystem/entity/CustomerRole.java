package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "customer_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_roles_customer_role",
                columnNames = {"customer_id", "role_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRole {
        @Id
        @SequenceGenerator(
                name = "customer_role_seq_gen",
                sequenceName = "customer_role_seq",
                allocationSize = 50
        )
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_role_seq_gen")
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "customer_id", nullable = false)
        private Customer customer;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "role_id",  nullable = false)
        private Role role;

        @Column(nullable = false, updatable = false)
        private Instant assignedAt;

        public CustomerRole(Customer customer, Role role) {
                this.customer = customer;
                this.role = role;
                this.assignedAt = Instant.now();
        }

        @Override
        public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CustomerRole that = (CustomerRole) o;
                return Objects.equals(customer, that.customer) && Objects.equals(role, that.role);
        }

        @Override
        public int hashCode() {
                return Objects.hash(customer, this.role);
        }
}
