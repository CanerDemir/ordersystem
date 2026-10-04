package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.CheckoutRequest;
import com.example.ordersystem.dto.response.OrderResponse;
import com.example.ordersystem.entity.*;
import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.exception.*;
import com.example.ordersystem.mapper.AddressMapper;
import com.example.ordersystem.mapper.OrderMapper;
import com.example.ordersystem.repository.CartRepository;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.OrderRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final AddressMapper addressMapper;

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request, Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new ResourceNotFoundException("Customer",  customerId));

        Cart cart = cartRepository.findByCustomer_Id(customerId).orElseThrow(() -> new CartNotFoundException(customerId));
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Set<Long> productIds = cart.getItems().stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

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

        validateStatusAndStock(cart.getItems(), productMap);

        Order order = new Order(
                OrderStatus.PENDING,
                customer,
                customer.getPhone(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                BigDecimal.ZERO
        );

        for (CartItem cartItem : cart.getItems()) {
            Product product = productMap.get(cartItem.getProduct().getId());
            OrderItem orderItem = new OrderItem(
                    product.getId(),
                    product.getName(),
                    cartItem.getQuantity(),
                    product.getPrice()
            );
            order.addOrderItem(orderItem);
            product.decreaseStock(cartItem.getQuantity());
        }

        Address shippingAddress = addressMapper.toEntity(request.shippingAddress());
        Address billingAddress = addressMapper.toEntity(request.billingAddress());
        order.assignAddresses(shippingAddress, billingAddress);

        Order savedOrder = orderRepository.save(order);

        cart.clear();

        return orderMapper.toOrderResponse(savedOrder);
    }

    private void validateStatusAndStock(List<CartItem> items, Map<Long, Product>  productMap) {
        List<ProductNotAvailableException.UnavailableProductInfo> unavailableProductInfoList = new ArrayList<>();
        List<InsufficientStockException.InsufficientStockDetail>  insufficientStockDetailList = new ArrayList<>();
        for (CartItem item : items) {
            Product product = productMap.get(item.getProduct().getId());
            if (!product.isActive()) {
                unavailableProductInfoList.add(new ProductNotAvailableException.UnavailableProductInfo(item.getProduct().getId(), product.getName(), product.getStatus()));
            } else if (product.getStock() < item.getQuantity()) {
                insufficientStockDetailList.add(new InsufficientStockException.InsufficientStockDetail(item.getProduct().getId(), product.getName(), item.getQuantity(), product.getStock()));
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
