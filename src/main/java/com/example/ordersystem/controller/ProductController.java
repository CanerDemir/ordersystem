package com.example.ordersystem.controller;

import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import com.example.ordersystem.service.interfaces.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATION', 'ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest productCreateRequest) {
        ProductResponse response = productService.create(productCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getById(@PathVariable("productId") Long productId) {
        ProductResponse response = productService.getById(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAll(@PageableDefault(page = 0, size = 20) Pageable pageable) {
        Page<ProductResponse> responsePage = productService.getAll(pageable);
        return ResponseEntity.ok(responsePage);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('OPERATION', 'ADMIN')")
    public ResponseEntity<ProductResponse>  update(@PathVariable("productId") Long productId, @Valid @RequestBody ProductUpdateRequest productUpdateRequest) {
        ProductResponse response = productService.update(productId, productUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{productId}/deactivate")
    @PreAuthorize("hasAnyRole('OPERATION', 'ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable("productId") Long productId) {
        productService.deactivate(productId);
        return ResponseEntity.noContent().build();
    }
}
