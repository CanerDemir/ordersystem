package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.exception.InvalidPageIndexException;
import com.example.ordersystem.exception.InvalidPageSizeException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.ProductMapper;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = Product.create(request.name(), request.price(), request.stock(),  request.description());
        Product savedProduct = productRepository.save(product);

        return productMapper.toProductResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAll(Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new InvalidPageIndexException(pageable.getPageNumber());
        }
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException(pageable.getPageSize(), MAX_PAGE_SIZE);
        }

        Page<Product> productPage = productRepository.findAll(pageable);
        return productPage.map(productMapper::toProductResponse);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.update(request.name(), request.price(), request.stock(), request.description());
        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.deactivate();
    }
}
