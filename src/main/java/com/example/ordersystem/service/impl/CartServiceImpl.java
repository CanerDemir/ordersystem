package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.entity.Cart;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.exception.CartNotFoundException;
import com.example.ordersystem.exception.ProductNotAvailableException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.CartMapper;
import com.example.ordersystem.repository.CartRepository;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long customerId) {
        return cartRepository.findByCustomerIdWithItemsAndProducts(customerId)
                .map(cartMapper::toCartResponse)
                .orElseGet(this::createEmptyCartResponse);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request, Long customerId) {
        Product product = productRepository.findById(request.productId()).orElseThrow(() -> new ResourceNotFoundException("Product",  request.productId()));

        if (!product.isActive()) {
            throw new ProductNotAvailableException(product.getId(), product.getName(), product.getStatus());
        }

        Cart cart =  cartRepository.findByCustomer_Id(customerId).orElseGet(() -> createNewCartForCustomer(customerId));

        cart.addProduct(product,  request.quantity());
        cartRepository.save(cart);
        return cartMapper.toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long customerId, Long productId, UpdateCartItemRequest request) {
        Cart  cart = cartRepository.findByCustomer_Id(customerId).orElseThrow(() -> new CartNotFoundException(customerId));
        Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product",  productId));

        if (!product.isActive()) {
            throw new ProductNotAvailableException(product.getId(), product.getName(), product.getStatus());
        }

        cart.updateProductQuantity(product, request.quantity());

        return cartMapper.toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long productId, Long customerId) {
        Cart  cart = cartRepository.findByCustomer_Id(customerId).orElseThrow(() -> new CartNotFoundException(customerId));
        Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product",  productId));

        cart.removeProduct(product);

        return cartMapper.toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(Long customerId) {
        Optional<Cart> cartOptional = cartRepository.findByCustomer_Id(customerId);

        // Cart henüz hiç oluşmamışsa idempotent olarak boş sepet yanıtı dönüyoruz
        if (cartOptional.isEmpty()) {
            return createEmptyCartResponse();
        }

        Cart cart = cartOptional.get();
        cart.clear();

        return cartMapper.toCartResponse(cart);
    }

    // --- Helper Methods ---

    private Cart createNewCartForCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        return new Cart(customer);
    }

    private CartResponse createEmptyCartResponse() {
        return new CartResponse(null, Collections.emptyList(), 0);
    }
}
