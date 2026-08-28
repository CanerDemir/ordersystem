package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.*;

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

    public Customer(String firstName, String lastName, String email,  String phone,  String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }
}
