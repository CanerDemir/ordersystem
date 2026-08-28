package com.example.ordersystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;

@Getter
public class ResourceNotFoundException extends BusinessException{
    private final String resourceName;
    private final List<?> missingIds;

    public ResourceNotFoundException(String resourceName, Object id) {
        super(
                String.format("%s bulunamadı. ID: %s", resourceName, id),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND"
        );
        this.resourceName = resourceName;
        this.missingIds = List.of(id);
    }

    public ResourceNotFoundException(String resourceName, Collection<?> missingIds) {
        super(
                String.format("%s kaynaklarından bazıları veritabanında bulunamadı. Bulunamayan ID'ler: %s", resourceName, missingIds),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND"
        );
        this.resourceName = resourceName;
        this.missingIds = List.copyOf(missingIds);
    }
}
