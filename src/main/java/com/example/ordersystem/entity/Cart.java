package com.example.ordersystem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Cart {

    @Id
    @SequenceGenerator(
            name = "cart_seq",
            sequenceName = "cart_seq",
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_seq")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id",  nullable = false, unique = true)
    private Customer customer;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items =  new ArrayList<>();

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

    public Cart(Customer customer) {
        this.customer = Objects.requireNonNull(customer, "Customer cannot be null");
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    // --- Domain Behaviors ---

    public void addProduct(Product product, Integer quantity) {
        Objects.requireNonNull(product, "Product cannot be null");

        Optional<CartItem> existingItem = findItemByProduct(product);

        if (existingItem.isPresent()) {
            existingItem.get().addQuantity(quantity);
        } else {
            CartItem newItem = new CartItem(this, product, quantity);
            this.items.add(newItem);
        }
    }

    public void updateProductQuantity(Product product, Integer newQuantity) {
        Objects.requireNonNull(product, "Product cannot be null");

        CartItem item = findItemByProduct(product)
                .orElseThrow(() -> new IllegalArgumentException("Product not found in cart: " + product.getId()));

        item.updateQuantity(newQuantity);
    }

    public void removeProduct(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");

        CartItem item = findItemByProduct(product)
                .orElseThrow(() -> new IllegalArgumentException("Product not found in cart: " + product.getId()));

        this.items.remove(item);
        item.setCart(null);
    }

    public void clear() {
        for (CartItem item : this.items) {
            item.setCart(null);
        }
        this.items.clear();
    }

    public Integer totalItemCount() {
        return this.items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    // --- Helper Methods & Encapsulated Accessors ---

    public Optional<CartItem> findItemByProduct(Product product) {
        if (product == null || product.getId() == null) {
            return Optional.empty();
        }
        return this.items.stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();
    }
}
