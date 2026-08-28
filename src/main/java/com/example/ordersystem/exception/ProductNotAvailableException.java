package com.example.ordersystem.exception;

import com.example.ordersystem.enums.ProductStatus;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

@Getter
public class ProductNotAvailableException extends BusinessException{
    private final List<UnavailableProductInfo> unavailableProducts;

    public ProductNotAvailableException(Long productId, String productName, ProductStatus currentStatus) {
        super(
                String.format("Ürün sipariş edilebilir durumda değil: '%s' (ID: %d). Mevcut Statü: %s (Gerekli Statü: ACTIVE)",
                        productName, productId, currentStatus),
                HttpStatus.CONFLICT,
                "PRODUCT_NOT_AVAILABLE"
        );
        this.unavailableProducts = List.of(new UnavailableProductInfo(productId, productName, currentStatus));
    }

    public ProductNotAvailableException(List<UnavailableProductInfo> unavailableProducts) {
        super(
                String.format("Siparişteki bazı ürünler aktif durumda değil: %s", unavailableProducts),
                HttpStatus.CONFLICT,
                "PRODUCT_NOT_AVAILABLE"
        );
        this.unavailableProducts = Collections.unmodifiableList(unavailableProducts);
    }

    public record UnavailableProductInfo(Long productId, String productName, ProductStatus status) {}
}
