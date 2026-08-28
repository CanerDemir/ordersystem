package com.example.ordersystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Set;

@Getter
public class DuplicateProductInOrderException extends BusinessException{
    private final Set<Long> duplicateProductIds;

    public DuplicateProductInOrderException(Set<Long> duplicateProductIds) {
        super(
                String.format("Siparişte aynı ürün birden fazla kez yer alamaz. Çakışan Ürün ID'leri: %s", duplicateProductIds),
                HttpStatus.BAD_REQUEST,
                "DUPLICATE_PRODUCT_IN_ORDER"
        );
        this.duplicateProductIds = Collections.unmodifiableSet(duplicateProductIds);
    }
}
