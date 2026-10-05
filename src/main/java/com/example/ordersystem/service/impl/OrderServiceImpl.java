package com.example.ordersystem.service.impl;

import com.example.ordersystem.auth.CurrentUser;
import com.example.ordersystem.dto.request.AddressRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.dto.response.OrderSummaryResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.exception.*;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

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
        order.cancel();

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

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getCustomerOrders(CurrentUser user, Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new InvalidPageIndexException(pageable.getPageNumber());
        }
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException(pageable.getPageSize(), MAX_PAGE_SIZE);
        }
        Sort deterministicSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), deterministicSort);
        return orderRepository.findOrderSummariesByCustomerId(user.customerId(), safePageable);
    }

    @Override
    @Transactional
    public OrderResponse updateShippingAddress(Long orderId, AddressRequest request, CurrentUser user) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, user.customerId()).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        Address newShippingAddress = new Address(
                request.title(),
                request.city(),
                request.district(),
                request.zipCode(),
                request.country(),
                request.addressLine(),
                request.addressDetail()
        );
        order.updateShippingAddress(newShippingAddress);
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse startPreparing(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.startPreparing();
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markAsShipped(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.markAsShipped();
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markAsDelivered(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.markAsDelivered();
        return orderMapper.toOrderResponse(order);
    }
}
