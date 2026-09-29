package com.example.ordersystem.service.interfaces;

import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse create(ProductCreateRequest productCreateRequest);
    ProductResponse getById(Long id);
    Page<ProductResponse> getAll(Pageable pageable);
    ProductResponse update(Long id, ProductUpdateRequest productUpdateRequest);
    void deactivate(Long id);
}
