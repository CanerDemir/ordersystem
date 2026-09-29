package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.ProductCreateRequest;
import com.example.ordersystem.dto.request.ProductUpdateRequest;
import com.example.ordersystem.dto.response.ProductResponse;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.InvalidPageIndexException;
import com.example.ordersystem.exception.InvalidPageSizeException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.ProductMapper;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductCreateRequest createRequest;
    private ProductUpdateRequest updateRequest;
    private Product product;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        createRequest = new ProductCreateRequest(
                "Laptop",
                BigDecimal.valueOf(15000.00),
                10,
                "Gaming Laptop"
        );

        updateRequest = new ProductUpdateRequest(
                "Updated Laptop",
                BigDecimal.valueOf(18000.00),
                15,
                "Updated Gaming Laptop"
        );

        product = Product.create("Laptop", BigDecimal.valueOf(15000.00), 10, "Gaming Laptop");

        productResponse = new ProductResponse(
                1L,
                "Laptop",
                BigDecimal.valueOf(15000.00),
                10,
                "Gaming Laptop",
                ProductStatus.ACTIVE,
                1L,
                Instant.now(),
                Instant.now()
        );
    }

    // =========================================================================
    // CREATE
    // =========================================================================
    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Başarılı oluşturma: Product ACTIVE durumda oluşturulmalı, save() çağrılmalı ve mapper sonucu dönmeli")
        void create_shouldCreateActiveProductAndReturnMappedResponse() {
            // Arrange
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(productMapper.toProductResponse(any(Product.class))).thenReturn(productResponse);

            // Act
            ProductResponse result = productService.create(createRequest);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Laptop");

            verify(productRepository).save(productCaptor.capture());
            Product savedProduct = productCaptor.getValue();

            assertThat(savedProduct.getName()).isEqualTo("Laptop");
            assertThat(savedProduct.getPrice()).isEqualTo(BigDecimal.valueOf(15000.00));
            assertThat(savedProduct.getStock()).isEqualTo(10);
            assertThat(savedProduct.getDescription()).isEqualTo("Gaming Laptop");
            assertThat(savedProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            verify(productMapper).toProductResponse(savedProduct);
        }
    }

    // =========================================================================
    // GET BY ID
    // =========================================================================
    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Başarılı: Doğru ID ile repository çağrılmalı ve DTO dönmeli")
        void getById_whenProductExists_shouldReturnProductResponse() {
            // Arrange
            Long productId = 1L;
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productMapper.toProductResponse(product)).thenReturn(productResponse);

            // Act
            ProductResponse result = productService.getById(productId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);

            verify(productRepository).findById(productId);
            verify(productMapper).toProductResponse(product);
        }

        @Test
        @DisplayName("Product bulunamadığında ResourceNotFoundException fırlatmalı")
        void getById_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
            // Arrange
            Long productId = 999L;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.getById(productId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository).findById(productId);
            verifyNoInteractions(productMapper);
        }
    }

    // =========================================================================
    // GET ALL
    // =========================================================================
    @Nested
    @DisplayName("getAll()")
    class GetAll {

        @Test
        @DisplayName("Başarılı: Pageable aktarılmalı ve Page<ProductResponse>'a dönüştürülmeli")
        void getAll_whenValidPageable_shouldReturnPageOfProductResponse() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> productPage = new PageImpl<>(List.of(product), pageable, 1);

            when(productRepository.findAll(pageable)).thenReturn(productPage);
            when(productMapper.toProductResponse(product)).thenReturn(productResponse);

            // Act
            Page<ProductResponse> result = productService.getAll(pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().getFirst().name()).isEqualTo("Laptop");

            verify(productRepository).findAll(pageable);
            verify(productMapper).toProductResponse(product);
        }

        @Test
        @DisplayName("Page size > 100 olduğunda InvalidPageSizeException fırlatmalı")
        void getAll_whenPageSizeExceedsMax_shouldThrowInvalidPageSizeException() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 101);

            // Act & Assert
            assertThatThrownBy(() -> productService.getAll(pageable))
                    .isInstanceOf(InvalidPageSizeException.class);

            verifyNoInteractions(productRepository);
            verifyNoInteractions(productMapper);
        }

        @Test
        @DisplayName("Page index < 0 olduğunda InvalidPageIndexException fırlatmalı")
        void getAll_whenPageIndexNegative_shouldThrowInvalidPageIndexException() {
            // Arrange
            Pageable pageable = mock(Pageable.class);

            when(pageable.getPageNumber()).thenReturn(-1);
            when(pageable.getPageSize()).thenReturn(10);

            // Act & Assert
            assertThatThrownBy(() -> productService.getAll(pageable))
                    .isInstanceOf(InvalidPageIndexException.class);

            verifyNoInteractions(productRepository);
            verifyNoInteractions(productMapper);
        }
    }

    // =========================================================================
    // UPDATE
    // =========================================================================
    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("Başarılı: Field'lar güncellenmeli, save() ÇAĞRILMAMALI (Dirty Checking), mapper sonucu dönmeli")
        void update_whenProductExists_shouldUpdateFieldsWithoutCallingSave() {
            // Arrange
            Long productId = 1L;
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productMapper.toProductResponse(product)).thenReturn(productResponse);

            // Act
            ProductResponse result = productService.update(productId, updateRequest);

            // Assert
            assertThat(result).isNotNull();

            // Managed Entity Mutation Assertion
            assertThat(product.getName()).isEqualTo("Updated Laptop");
            assertThat(product.getPrice()).isEqualTo(BigDecimal.valueOf(18000.00));
            assertThat(product.getStock()).isEqualTo(15);
            assertThat(product.getDescription()).isEqualTo("Updated Gaming Laptop");

            // Explicit repository.save() çağrılmadığını doğrulama
            verify(productRepository).findById(productId);
            verify(productRepository, never()).save(any());
            verify(productMapper).toProductResponse(product);
        }

        @Test
        @DisplayName("Güncellenecek ürün bulunamadığında ResourceNotFoundException fırlatmalı")
        void update_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
            // Arrange
            Long productId = 999L;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.update(productId, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository).findById(productId);
            verify(productRepository, never()).save(any());
            verifyNoInteractions(productMapper);
        }
    }

    // =========================================================================
    // DEACTIVATE
    // =========================================================================
    @Nested
    @DisplayName("deactivate()")
    class Deactivate {

        @Test
        @DisplayName("Başarılı: deactivate() metod çağrılmalı, save() ÇAĞRILMAMALI")
        void deactivate_whenProductExists_shouldDeactivateWithoutCallingSave() {
            // Arrange
            Long productId = 1L;
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            productService.deactivate(productId);

            // Assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.PASSIVE);

            verify(productRepository).findById(productId);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deactivate edilecek ürün bulunamadığında ResourceNotFoundException fırlatmalı")
        void deactivate_whenProductDoesNotExist_shouldThrowResourceNotFoundException() {
            // Arrange
            Long productId = 999L;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.deactivate(productId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository).findById(productId);
            verify(productRepository, never()).save(any());
        }
    }
}