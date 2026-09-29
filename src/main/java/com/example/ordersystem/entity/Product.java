package com.example.ordersystem.entity;

import com.example.ordersystem.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Product {

    @Id
    @SequenceGenerator(name = "product_seq", allocationSize = 50, sequenceName = "product_seq")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void decreaseStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException( "Quantity must be greater than zero. Provided Quantity: " + quantity );
        }

        if (this.stock < quantity) {
            throw new IllegalArgumentException( "Insufficient stock for this product!" );
        }

        this.stock -= quantity;
    }

    public void increaseStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException( "Quantity must be greater than zero. Provided Quantity: " + quantity );
        }

        this.stock += quantity;
    }

    public void deactivate() {
        this.status = ProductStatus.PASSIVE;
    }

    public static Product create(String name, BigDecimal price, Integer stock, String description) {
        Product product = new Product();
        product.name = name;
        product.price = price;
        product.stock = stock;
        product.description = description;
        product.status = ProductStatus.ACTIVE;

        return product;
    }

    public void update(String name, BigDecimal price, Integer stock, String description) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
    }
}
