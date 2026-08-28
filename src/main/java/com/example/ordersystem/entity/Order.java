package com.example.ordersystem.entity;

import com.example.ordersystem.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class Order {

    @Id
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant paidAt;
    private Instant confirmedAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String customerPhone;

    @Column(nullable = false)
    private String customerFirstName;

    @Column(nullable = false)
    private String customerLastName;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "order")
    @Setter(AccessLevel.NONE)
    private List<OrderItem> items= new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "shipping_address_title", length = 100)),
            @AttributeOverride(name = "city", column = @Column(name = "shipping_city", nullable = false, length = 100)),
            @AttributeOverride(name = "district", column = @Column(name = "shipping_district", nullable = false, length = 100)),
            @AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip_code", length = 20)),
            @AttributeOverride(name = "country", column = @Column(name = "shipping_country", nullable = false, length = 100)),
            @AttributeOverride(name = "addressLine", column = @Column(name = "shipping_address_line", nullable = false, length = 500)),
            @AttributeOverride(name = "addressDetail", column = @Column(name = "shipping_address_detail", length = 500))
    })
    private Address shippingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "title", column = @Column(name = "billing_address_title", length = 100)),
            @AttributeOverride(name = "city", column = @Column(name = "billing_city", nullable = false, length = 100)),
            @AttributeOverride(name = "district", column = @Column(name = "billing_district", nullable = false, length = 100)),
            @AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip_code", length = 20)),
            @AttributeOverride(name = "country", column = @Column(name = "billing_country", nullable = false, length = 100)),
            @AttributeOverride(name = "addressLine", column = @Column(name = "billing_address_line", nullable = false, length = 500)),
            @AttributeOverride(name = "addressDetail", column = @Column(name = "billing_address_detail", length = 500))
    })
    private Address billingAddress;

    @Version
    private Long version;

    public Order( OrderStatus status, Customer customer, String customerPhone, String customerFirstName, String customerLastName, String customerEmail, BigDecimal totalAmount, Instant createdAt ) {
        this.status = status;
        this.customer = customer;
        this.customerPhone = customerPhone;
        this.customerFirstName = customerFirstName;
        this.customerLastName = customerLastName;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public void addOrderItem(OrderItem item){
        if (item == null) {
            throw new IllegalArgumentException("Cannot add null order item");
        }

        this.items.add(item);
        this.totalAmount = totalAmount.add(item.getLineTotal());
        item.setOrder(this);
    }
}
