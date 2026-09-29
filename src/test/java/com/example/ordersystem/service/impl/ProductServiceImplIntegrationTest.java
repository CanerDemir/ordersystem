package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.repository.ProductRepository;
import com.example.ordersystem.service.interfaces.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductServiceImplIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    // =========================================================================
    // CREATE INTEGRATION TEST
    // =========================================================================
    @Nested
    @DisplayName("create() Integration")
    class CreateIntegration {

        @Test
        @DisplayName("Görünür DB persistence ve @PrePersist auditor/version alanlarının çalıştığını doğrulamalı")
        void create_shouldPersistProductToDatabaseWithAuditAndVersion() {
            // Arrange
            ProductCreateRequest request = new ProductCreateRequest(
                    "Gaming Monitor",
                    BigDecimal.valueOf(8500.00),
                    15,
                    "4K Ultra HD Monitor"
            );

            // Act
            ProductResponse response = productService.create(request);

            // Assert Response
            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.name()).isEqualTo("Gaming Monitor");
            assertThat(response.price()).isEqualTo(BigDecimal.valueOf(8500.00));
            assertThat(response.stock()).isEqualTo(15);
            assertThat(response.description()).isEqualTo("4K Ultra HD Monitor");

            // Flush & Clear Persistence Context to ensure reading directly from DB
            entityManager.flush();
            entityManager.clear();

            // Assert Database Persistence
            Product dbProduct = productRepository.findById(response.id())
                    .orElseThrow(() -> new AssertionError("Product should exist in database"));

            assertThat(dbProduct.getId()).isEqualTo(response.id());
            assertThat(dbProduct.getName()).isEqualTo("Gaming Monitor");
            assertThat(dbProduct.getPrice()).isEqualTo(BigDecimal.valueOf(8500.00));
            assertThat(dbProduct.getStock()).isEqualTo(15);
            assertThat(dbProduct.getDescription()).isEqualTo("4K Ultra HD Monitor");
            assertThat(dbProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            // Audit & Version Verification
            assertThat(dbProduct.getVersion()).isNotNull();
            assertThat(dbProduct.getCreatedAt()).isNotNull();
            assertThat(dbProduct.getUpdatedAt()).isNotNull();
        }
    }

    // =========================================================================
    // GET BY ID INTEGRATION TEST
    // =========================================================================
    @Nested
    @DisplayName("getById() Integration")
    class GetByIdIntegration {

        @Test
        @DisplayName("DB'deki gerçek entity ile DTO yanıtının eşleştiğini doğrulamalı")
        void getById_whenProductExists_shouldReturnMatchingDto() {
            // Arrange
            Product product = Product.create("Mechanical Keyboard", BigDecimal.valueOf(2500.00), 50, "RGB Keyboard");
            Product savedProduct = productRepository.saveAndFlush(product);
            entityManager.clear();

            // Act
            ProductResponse response = productService.getById(savedProduct.getId());

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(savedProduct.getId());
            assertThat(response.name()).isEqualTo("Mechanical Keyboard");
            assertThat(response.price()).isEqualTo(BigDecimal.valueOf(2500.00));
            assertThat(response.stock()).isEqualTo(50);
            assertThat(response.description()).isEqualTo("RGB Keyboard");
        }

        @Test
        @DisplayName("Var olmayan ID arandığında ResourceNotFoundException fırlatmalı")
        void getById_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
            Long nonExistingId = 999999L;

            assertThatThrownBy(() -> productService.getById(nonExistingId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // UPDATE INTEGRATION TEST (JPA Dirty Checking & @PreUpdate Verification)
    // =========================================================================
    @Nested
    @DisplayName("update() Integration")
    class UpdateIntegration {

        @Test
        @DisplayName("JPA Dirty Checking, @PreUpdate timestamp güncellemesi ve @Version artışını doğrulamalı")
        void update_shouldTriggerDirtyCheckingAndIncrementVersionAndTimestamp() {
            // Arrange: Ilk urunu olustur ve DB'ye yaz
            Product initialProduct = Product.create("Old Mouse", BigDecimal.valueOf(500.00), 20, "Old Description");
            Product savedProduct = productRepository.saveAndFlush(initialProduct);

            Long productId = savedProduct.getId();
            Long beforeVersion = savedProduct.getVersion();
            Instant beforeUpdatedAt = savedProduct.getUpdatedAt();

            entityManager.clear();

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "Wireless Mouse",
                    BigDecimal.valueOf(1200.00),
                    35,
                    "Ergonomic Wireless Mouse"
            );

            // Act: Service update çağrısı (Dirty Checking burada devrededir)
            ProductResponse response = productService.update(productId, updateRequest);

            // Flush & Clear to trigger SQL UPDATE and sync first-level cache with DB
            entityManager.flush();
            entityManager.clear();

            // Assert: DB'den tekrar oku ve state degisimlerini kontrol et
            Product updatedDbProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new AssertionError("Updated product should exist in DB"));

            assertThat(updatedDbProduct.getId()).isEqualTo(productId);
            assertThat(updatedDbProduct.getName()).isEqualTo("Wireless Mouse");
            assertThat(updatedDbProduct.getPrice()).isEqualTo(BigDecimal.valueOf(1200.00));
            assertThat(updatedDbProduct.getStock()).isEqualTo(35);
            assertThat(updatedDbProduct.getDescription()).isEqualTo("Ergonomic Wireless Mouse");
            assertThat(updatedDbProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            // Version increment assertion
            assertThat(updatedDbProduct.getVersion()).isGreaterThan(beforeVersion);

            // Timestamp modification assertion
            assertThat(updatedDbProduct.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);

            // Response assertions
            assertThat(response.name()).isEqualTo("Wireless Mouse");
        }
    }

    // =========================================================================
    // DEACTIVATE INTEGRATION TEST
    // =========================================================================
    @Nested
    @DisplayName("deactivate() Integration")
    class DeactivateIntegration {

        @Test
        @DisplayName("Product statüsünün INACTIVE/PASSIVE yapıldığını ve audit/version alanlarının güncellendiğini doğrulamalı")
        void deactivate_shouldChangeStatusToPassiveAndIncrementVersion() {
            // Arrange
            Product product = Product.create("Active Product", BigDecimal.valueOf(100.00), 10, "Description");
            Product savedProduct = productRepository.saveAndFlush(product);

            Long productId = savedProduct.getId();
            Long beforeVersion = savedProduct.getVersion();
            Instant beforeUpdatedAt = savedProduct.getUpdatedAt();

            entityManager.clear();

            // Act
            productService.deactivate(productId);

            entityManager.flush();
            entityManager.clear();

            // Assert
            Product deactivatedProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new AssertionError("Deactivated product should exist in DB"));

            assertThat(deactivatedProduct.getStatus()).isEqualTo(ProductStatus.PASSIVE);
            assertThat(deactivatedProduct.getVersion()).isGreaterThan(beforeVersion);
            assertThat(deactivatedProduct.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
        }
    }

    // =========================================================================
    // PAGINATION INTEGRATION TEST
    // =========================================================================
    @Nested
    @DisplayName("getAll() Pagination Integration")
    class PaginationIntegration {

        @Test
        @DisplayName("Gerçek Spring Data JPA Pageable ile sayfalama ve DTO dönüşümünü doğrulamalı")
        void getAll_shouldReturnPaginatedProductResponseDtos() {
            // Arrange: DB'de 3 farklı ürün oluştur
            Product p1 = Product.create("Product A", BigDecimal.valueOf(100.00), 10, "Desc A");
            Product p2 = Product.create("Product B", BigDecimal.valueOf(200.00), 20, "Desc B");
            Product p3 = Product.create("Product C", BigDecimal.valueOf(300.00), 30, "Desc C");

            productRepository.saveAllAndFlush(java.util.List.of(p1, p2, p3));
            entityManager.clear();

            // Act 1: Sayfa 0 (Size: 2)
            PageRequest page0Request = PageRequest.of(0, 2);
            Page<ProductResponse> page0 = productService.getAll(page0Request);

            // Assert 1
            assertThat(page0.getTotalElements()).isEqualTo(3);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getContent().getFirst()).isInstanceOf(ProductResponse.class);

            // Act 2: Sayfa 1 (Size: 2)
            PageRequest page1Request = PageRequest.of(1, 2);
            Page<ProductResponse> page1 = productService.getAll(page1Request);

            // Assert 2
            assertThat(page1.getTotalElements()).isEqualTo(3);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page1.getContent().getFirst()).isInstanceOf(ProductResponse.class);
        }
    }
}