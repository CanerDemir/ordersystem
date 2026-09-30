package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_item_cart_product",
                        columnNames = {"cart_id", "product_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CartItem {

    @Id
    @SequenceGenerator(
            name = "cart_item_seq",
            sequenceName = "cart_item_seq",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_item_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id",  nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id",  nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public CartItem(Cart cart, Product product, Integer quantity) {
        setCart(cart);
        this.product = Objects.requireNonNull(product, "Product cannot be null");
        setQuantity(quantity);
    }

    public void updateQuantity(Integer newQuantity) {
        setQuantity(newQuantity);
    }

    public void addQuantity(Integer additionalQuantity) {
        if (additionalQuantity == null || additionalQuantity <= 0) {
            throw new IllegalArgumentException("Quantity increment must be greater than zero");
        }
        this.quantity += additionalQuantity;
    }

    private void setQuantity(Integer newQuantity) {
        if (newQuantity == null || newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        this.quantity = newQuantity;
    }

    void setCart(Cart cart) {
        this.cart = cart;
    }

    public BigDecimal subtotal() {
        return this.product.getPrice().multiply(BigDecimal.valueOf(this.quantity));
    }
}
