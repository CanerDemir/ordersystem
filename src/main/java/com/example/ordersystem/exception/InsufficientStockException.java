package com.example.ordersystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

@Getter
public class InsufficientStockException extends BusinessException{
    private final List<InsufficientStockDetail> insufficientProducts;

    public InsufficientStockException(Long productId, String productName, Integer requestedQuantity, Integer availableStock) {
        super(
                String.format("Yetersiz stok! Ürün: '%s' (ID: %d) - İstenen: %d, Mevcut: %d",
                        productName, productId, requestedQuantity, availableStock),
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK"
        );
        this.insufficientProducts = List.of(
                new InsufficientStockDetail(productId, productName, requestedQuantity, availableStock)
        );
    }

    public InsufficientStockException(List<InsufficientStockDetail> insufficientProducts) {
        super(
                String.format("Siparişteki bazı ürünlerin stoğu yetersiz: %s", insufficientProducts),
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK"
        );
        this.insufficientProducts = Collections.unmodifiableList(insufficientProducts);
    }

    public record InsufficientStockDetail(
            Long productId,
            String productName,
            Integer requestedQuantity,
            Integer availableStock
    ) {}
}
