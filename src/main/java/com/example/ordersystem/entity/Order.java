package com.example.ordersystem.entity;

import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.exception.OrderCannotBeCancelledException;
import com.example.ordersystem.exception.OrderCannotBePaidException;
import com.example.ordersystem.exception.OrderCannotBeUpdatedException;
import com.example.ordersystem.exception.OrderStatusTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
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
    private Instant refundedAt;

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

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }

    public Order( OrderStatus status, Customer customer, String customerPhone, String customerFirstName, String customerLastName, String customerEmail, BigDecimal totalAmount ) {
        this.status = status;
        this.customer = customer;
        this.customerPhone = customerPhone;
        this.customerFirstName = customerFirstName;
        this.customerLastName = customerLastName;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public void addOrderItem(OrderItem item){
        if (item == null) {
            throw new IllegalArgumentException("Cannot add null order item");
        }
        if (item.getOrder() != null) {
            throw new IllegalStateException("OrderItem is already assigned to an order");
        }
        if (items.contains(item)) {
            throw new IllegalStateException("OrderItem instance is already present in this order");
        }

        this.items.add(item);
        this.totalAmount = totalAmount.add(item.getLineTotal());
        item.setOrder(this);
    }

    public void validateCanBePaid() {
        if (this.status != OrderStatus.PENDING) {
            throw new OrderCannotBePaidException(this.id, this.status);
        }
    }

    public void updateShippingAddress(Address address) {
        if (this.status != OrderStatus.PENDING) {
            throw new OrderCannotBeUpdatedException(this.id, this.status);
        }
        this.shippingAddress = address;
    }

    public void assignAddresses(Address shippingAddress, Address billingAddress) {
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress cannot be null");
        this.billingAddress = Objects.requireNonNull(billingAddress, "billingAddress cannot be null");
    }

    public boolean canBeRefunded() {
        return this.status == OrderStatus.PAID || this.status == OrderStatus.PREPARING || this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED;
    }

    // #####################
    // Life Cycle Methods
    // #####################

    public void markAsPaid() {
        this.validateCanBePaid();
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void startPreparing() {
        if (this.status != OrderStatus.PAID) {
            throw new OrderStatusTransitionException(this.id, this.status, OrderStatus.PREPARING);
        }

        this.status = OrderStatus.PREPARING;
    }

    public void markAsShipped() {
        if (this.status != OrderStatus.PREPARING) {
            throw new OrderStatusTransitionException(this.id, this.status, OrderStatus.SHIPPED);
        }

        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markAsDelivered() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new OrderStatusTransitionException(this.id, this.status, OrderStatus.DELIVERED);
        }

        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void refund() {
        switch (this.status) {
            case PAID, PREPARING, SHIPPED, DELIVERED -> {
                this.status = OrderStatus.REFUNDED;
                this.refundedAt = Instant.now();
            }
            default -> throw new OrderStatusTransitionException(this.id, this.status, OrderStatus.REFUNDED);
        }
    }

    public Order cancel() {
        if (this.status != OrderStatus.PENDING) {
            throw new OrderCannotBeCancelledException(this.id);
        }

        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        return this;
    }
}
