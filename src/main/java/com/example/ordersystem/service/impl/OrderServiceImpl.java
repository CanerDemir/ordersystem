package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.CreateOrderRequest;
import com.example.ordersystem.dto.request.OrderItemRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.*;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, CurrentUser user) {
        Customer customer = customerRepository.findById(user.customerId()).orElseThrow(() -> new ResourceNotFoundException("Customer", user.customerId()));

        Set<Long> productIds = extractAndValidateProductIds(request.items());

        List<Product> productList = productRepository.findAllByIdInWithLock(productIds);

        if (productList.size() != productIds.size()) {
            Set<Long> foundProductIdsSet = new HashSet<>();
            for (Product product : productList) {
                foundProductIdsSet.add(product.getId());
            }
            Set<Long> missingProductIdsSet = new HashSet<>(productIds);
            missingProductIdsSet.removeAll(foundProductIdsSet);
            throw new ResourceNotFoundException("Product", missingProductIdsSet);
        }

        Map<Long, Product> productMap = productList.stream().collect(Collectors.toMap(Product::getId, p -> p));

        Order order = new Order(
                OrderStatus.PENDING,
                customer,
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                BigDecimal.ZERO,
                Instant.now()
        );

        validateStatusAndStock(request.items(), productMap);

        for (OrderItemRequest item : request.items()) {
            Product product = productMap.get(item.productId());
            OrderItem orderItem = new OrderItem(
                    item.productId(),
                    product.getName(),
                    item.quantity(),
                    product.getPrice(),
                    product.getPrice().multiply(new BigDecimal(item.quantity()))
            );
            order.addOrderItem(orderItem);
            product.decreaseStock(item.quantity());
        }

        Address shipAddress = new Address(
                request.shippingAddress().title(),
                request.shippingAddress().city(),
                request.shippingAddress().district(),
                request.shippingAddress().zipCode(),
                request.shippingAddress().country(),
                request.shippingAddress().addressLine(),
                request.shippingAddress().addressDetail()
        );
        Address billAddress = new Address(
                request.billingAddress().title(),
                request.billingAddress().city(),
                request.billingAddress().district(),
                request.billingAddress().zipCode(),
                request.billingAddress().country(),
                request.billingAddress().addressLine(),
                request.billingAddress().addressDetail()
        );
        order.setShippingAddress(shipAddress);
        order.setBillingAddress(billAddress);

        Order savedOrder = orderRepository.save(order);

        return orderMapper.toOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, CurrentUser user) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, user.customerId()).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, CurrentUser user) {
        Order order = orderRepository.findByIdAndCustomerIdWithLock(orderId, user.customerId()).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCannotBeCancelledException(orderId);
        }

        Set<Long> productIds = order.getItems().stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        List<Product> lockedProducts = productRepository.findAllByIdInWithLock(productIds);
        Map<Long, Product> productMap = lockedProducts.stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        for (OrderItem orderItem : order.getItems()) {
            Long productId = orderItem.getProductId();
            Product product = productMap.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product", productId);
            }
            product.increaseStock(orderItem.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toOrderResponse(savedOrder);
    }

    private Set<Long> extractAndValidateProductIds(List<OrderItemRequest> items) {
        Set<Long> seenProductIds = new HashSet<>();
        Set<Long> duplicateProductIds = new HashSet<>();

        for (OrderItemRequest item : items) {
            if (!seenProductIds.add(item.productId())) {
                duplicateProductIds.add(item.productId());
            }
        }

        if (!duplicateProductIds.isEmpty()) {
            throw new DuplicateProductInOrderException(duplicateProductIds);
        }

        return new TreeSet<>(seenProductIds);
    }

    private void validateStatusAndStock(List<OrderItemRequest> items, Map<Long, Product>  productMap) {
        List<ProductNotAvailableException.UnavailableProductInfo> unavailableProductInfoList = new ArrayList<>();
        List<InsufficientStockException.InsufficientStockDetail>  insufficientStockDetailList = new ArrayList<>();
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());
            if (!product.getStatus().equals(ProductStatus.ACTIVE)) {
                unavailableProductInfoList.add(new ProductNotAvailableException.UnavailableProductInfo(item.productId(), product.getName(), product.getStatus()));
            }
            if (product.getStock() < item.quantity()) {
                insufficientStockDetailList.add(new InsufficientStockException.InsufficientStockDetail(item.productId(), product.getName(), item.quantity(), product.getStock()));
            }
        }
        if (!unavailableProductInfoList.isEmpty()) {
            throw new ProductNotAvailableException(unavailableProductInfoList);
        }
        if (!insufficientStockDetailList.isEmpty()) {
            throw new InsufficientStockException(insufficientStockDetailList);
        }
    }
}
