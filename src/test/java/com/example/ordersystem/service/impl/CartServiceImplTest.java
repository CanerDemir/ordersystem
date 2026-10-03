package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.request.AddCartItemRequest;
import com.example.ordersystem.dto.request.UpdateCartItemRequest;
import com.example.ordersystem.dto.response.CartItemResponse;
import com.example.ordersystem.dto.response.CartResponse;
import com.example.ordersystem.entity.Cart;
import com.example.ordersystem.entity.Customer;
import com.example.ordersystem.entity.Product;
import com.example.ordersystem.enums.ProductStatus;
import com.example.ordersystem.exception.CartItemNotFoundException;
import com.example.ordersystem.exception.CartNotFoundException;
import com.example.ordersystem.exception.ProductNotAvailableException;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.CartMapper;
import com.example.ordersystem.repository.CartRepository;
import com.example.ordersystem.repository.CustomerRepository;
import com.example.ordersystem.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CartMapper cartMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @Nested
    @DisplayName("getCart Tests")
    class GetCartTests {

        @Test
        @DisplayName("When cart exists for customer, should return mapped CartResponse")
        void getCart_WhenCartExists_ShouldReturnCartResponse() {
            // Given
            Long customerId = 1L;
            Cart mockCart = mock(Cart.class);

            CartItemResponse itemResponse = new CartItemResponse(
                    10L,
                    "Test Product",
                    BigDecimal.valueOf(100),
                    2,
                    BigDecimal.valueOf(200)
            );

            CartResponse expectedResponse = new CartResponse(
                    42L,
                    List.of(itemResponse),
                    2
            );

            given(cartRepository.findByCustomerIdWithItemsAndProducts(customerId))
                    .willReturn(Optional.of(mockCart));
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.getCart(customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);
            assertThat(actualResponse.totalItemCount()).isEqualTo(2);
            assertThat(actualResponse.items()).containsExactly(itemResponse);

            verify(cartRepository).findByCustomerIdWithItemsAndProducts(customerId);
            verify(cartMapper).toCartResponse(mockCart);
        }

        @Test
        @DisplayName("When cart does not exist for customer, should return empty CartResponse without invoking mapper")
        void getCart_WhenCartDoesNotExist_ShouldReturnEmptyResponseWithoutInvokingMapper() {
            // Given
            Long customerId = 1L;

            given(cartRepository.findByCustomerIdWithItemsAndProducts(customerId))
                    .willReturn(Optional.empty());

            // When
            CartResponse actualResponse = cartService.getCart(customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isNull();
            assertThat(actualResponse.items()).isEmpty();
            assertThat(actualResponse.totalItemCount()).isZero();

            verify(cartRepository).findByCustomerIdWithItemsAndProducts(customerId);
            verifyNoInteractions(cartMapper);
        }
    }

    @Nested
    @DisplayName("addItem Tests")
    class AddItemTests {

        @Test
        @DisplayName("When product is not found, should throw ProductNotFoundException")
        void addItem_WhenProductNotFound_ShouldThrowProductNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);

            given(productRepository.findById(productId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.addItem(request, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(productId.toString());

            verify(productRepository).findById(productId);
            verifyNoInteractions(cartRepository, customerRepository, cartMapper);
        }

        @Test
        @DisplayName("When product is inactive, should throw ProductNotAvailableException")
        void addItem_WhenProductIsInactive_ShouldThrowProductNotAvailableException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);

            Product mockProduct = mock(Product.class);
            when(mockProduct.getId()).thenReturn(productId);
            when(mockProduct.getName()).thenReturn("Test Product");
            when(mockProduct.getStatus()).thenReturn(ProductStatus.PASSIVE);

            given(mockProduct.isActive()).willReturn(false);

            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));

            // When & Then
            assertThatThrownBy(() -> cartService.addItem(request, customerId))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(productId.toString());

            verify(productRepository).findById(productId);
            verifyNoInteractions(cartRepository, customerRepository, cartMapper);
        }

        @Test
        @DisplayName("When cart exists and product is active, should add product to existing cart")
        void addItem_WhenCartExists_ShouldAddProductToExistingCart() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            Integer quantity = 2;
            AddCartItemRequest request = new AddCartItemRequest(productId, quantity);

            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            Cart mockCart = mock(Cart.class);

            CartResponse expectedResponse = new CartResponse(42L, Collections.emptyList(), quantity);

            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(cartRepository.save(mockCart))
                    .willReturn(mockCart);
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.addItem(request, customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);

            verify(mockCart).addProduct(mockProduct, quantity);
            verify(cartRepository).save(mockCart);
            verify(cartMapper).toCartResponse(mockCart);
            verifyNoInteractions(customerRepository);
        }

        @Test
        @DisplayName("When cart does not exist, should fetch customer, create new cart and add product")
        void addItem_WhenCartDoesNotExist_ShouldFetchCustomerCreateCartAndAddProduct() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            Integer quantity = 3;
            AddCartItemRequest request = new AddCartItemRequest(productId, quantity);

            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            Customer mockCustomer = mock(Customer.class);

            Cart mockCart = mock(Cart.class);

            CartResponse expectedResponse = new CartResponse(100L, Collections.emptyList(), quantity);

            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());
            given(customerRepository.findById(customerId))
                    .willReturn(Optional.of(mockCustomer));
            given(cartRepository.save(any(Cart.class)))
                    .willReturn(mockCart);
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.addItem(request, customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(100L);

            verify(customerRepository).findById(customerId);
            verify(cartRepository).save(any(Cart.class));
            verify(cartMapper).toCartResponse(mockCart);
        }

        @Test
        @DisplayName("When cart does not exist and customer is not found, should throw ResourceNotFoundException")
        void addItem_WhenCartDoesNotExistAndCustomerNotFound_ShouldThrowException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);

            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());
            given(customerRepository.findById(customerId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.addItem(request, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verify(customerRepository).findById(customerId);
            verify(cartRepository, never()).save(any());
            verifyNoInteractions(cartMapper);
        }

        @Test
        @DisplayName("When cart does not exist, should create new cart with fetched customer, add product, save and map exact cart instance")
        void addItem_WhenCartDoesNotExist_ShouldCreateCartWithCustomerAddProductAndSave() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            Integer quantity = 3;
            AddCartItemRequest request = new AddCartItemRequest(productId, quantity);

            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            Customer mockCustomer = mock(Customer.class);
            CartResponse expectedResponse = new CartResponse(100L, Collections.emptyList(), quantity);

            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());
            given(customerRepository.findById(customerId))
                    .willReturn(Optional.of(mockCustomer));

            // save çağrıldığında kendisine iletilen Cart nesnesini doğrudan geri dönüyoruz
            given(cartRepository.save(any(Cart.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(cartMapper.toCartResponse(any(Cart.class)))
                    .willReturn(expectedResponse);

            ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);

            // When
            CartResponse actualResponse = cartService.addItem(request, customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(100L);

            // Customer doğrulama
            verify(customerRepository).findById(customerId);

            // Save edilen Cart instansını yakalama ve durum doğrulamaları
            verify(cartRepository).save(cartCaptor.capture());
            Cart capturedCart = cartCaptor.getValue();

            assertThat(capturedCart).isNotNull();
            assertThat(capturedCart.getCustomer()).isEqualTo(mockCustomer);

            verify(capturedCart).addProduct(mockProduct, quantity);

            // Mapper'a tam olarak kaydedilen Cart instansının iletildiğinin doğrulanması
            verify(cartMapper).toCartResponse(capturedCart);
        }
    }

    @Nested
    @DisplayName("updateItem Tests")
    class UpdateItemTests {

        @Test
        @DisplayName("When cart is not found for customer, should throw CartNotFoundException")
        void updateItem_WhenCartNotFound_ShouldThrowCartNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(2);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.updateItem( productId, customerId, request))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verifyNoInteractions(productRepository, cartMapper);
        }

        @Test
        @DisplayName("When product is not found in repository, should throw ProductNotFoundException")
        void updateItem_WhenProductNotFound_ShouldThrowProductNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(2);
            Cart mockCart = mock(Cart.class);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.updateItem(productId, customerId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(productId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verifyNoInteractions(cartMapper);
        }

        @Test
        @DisplayName("When product is inactive, should throw ProductNotActiveException")
        void updateItem_WhenProductIsInactive_ShouldThrowProductNotActiveException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(2);

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);
            when(mockProduct.getId()).thenReturn(productId);
            when(mockProduct.getName()).thenReturn("Test Product");
            when(mockProduct.getStatus()).thenReturn(ProductStatus.PASSIVE);
            given(mockProduct.isActive()).willReturn(false);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));

            // When & Then
            assertThatThrownBy(() -> cartService.updateItem(productId, customerId, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining(productId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verifyNoInteractions(cartMapper);
            verifyNoInteractions(mockCart);
        }

        @Test
        @DisplayName("When product is not in cart, cart.updateProductQuantity should throw IllegalArgumentException and service rethrow CartItemNotFoundException without calling mapper")
        void updateItem_WhenProductNotInCart_ShouldThrowCartItemNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            UpdateCartItemRequest request = new UpdateCartItemRequest(5);

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));

            // Domain metodu "ürün sepette yok" anlamında CartItemNotFoundException fırlatıyor
            doThrow(new CartItemNotFoundException(productId))
                    .when(mockCart)
                    .updateProductQuantity(mockProduct, request.quantity());

            // When & Then
            assertThatThrownBy(() -> cartService.updateItem(productId, customerId, request))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining(productId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verify(mockCart).updateProductQuantity(mockProduct, request.quantity());
            verifyNoInteractions(cartMapper);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("When cart and product exist and product is active, should update quantity via domain method and rely on dirty checking without explicit save")
        void updateItem_WhenValidRequest_ShouldUpdateQuantityAndReturnMappedResponse() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            Integer newQuantity = 5;
            UpdateCartItemRequest request = new UpdateCartItemRequest(newQuantity);

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);
            given(mockProduct.isActive()).willReturn(true);

            CartResponse expectedResponse = new CartResponse(42L, Collections.emptyList(), newQuantity);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.updateItem(productId, customerId, request);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verify(mockCart).updateProductQuantity(mockProduct, newQuantity);
            verify(cartMapper).toCartResponse(mockCart);

            // JPA Dirty Checking doğrulaması: Explicit save() çağrısı yapılmamalı!
            verify(cartRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeItem Tests")
    class RemoveItemTests {

        @Test
        @DisplayName("When cart is not found for customer, should throw CartNotFoundException")
        void removeItem_WhenCartNotFound_ShouldThrowCartNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.removeItem(productId, customerId))
                    .isInstanceOf(CartNotFoundException.class)
                    .hasMessageContaining(customerId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verifyNoInteractions(productRepository, cartMapper);
        }

        @Test
        @DisplayName("When product is not found in repository, should throw ProductNotFoundException")
        void removeItem_WhenProductNotFound_ShouldThrowProductNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;
            Cart mockCart = mock(Cart.class);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> cartService.removeItem(productId, customerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(productId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verifyNoInteractions(cartMapper);
        }

        @Test
        @DisplayName("When product is not in cart, cart.removeProduct should throw CartItemNotFoundException")
        void removeItem_WhenProductNotInCart_ShouldThrowCartItemNotFoundException() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);
            when(mockProduct.getId()).thenReturn(productId);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));

            doThrow(new CartItemNotFoundException(productId))
                    .when(mockCart).removeProduct(mockProduct);

            // When & Then
            assertThatThrownBy(() -> cartService.removeItem(productId, customerId))
                    .isInstanceOf(CartItemNotFoundException.class)
                    .hasMessageContaining(productId.toString());

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verify(mockCart).removeProduct(mockProduct);
            verifyNoInteractions(cartMapper);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("When product is inactive but present in cart, should successfully remove product from cart")
        void removeItem_WhenProductIsInactiveButInCart_ShouldRemoveProductSuccessfully() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);

            // Ürün pasif olmasına rağmen silme işlemine engel teşkil etmemeli
            given(mockProduct.isActive()).willReturn(false);

            CartResponse expectedResponse = new CartResponse(42L, Collections.emptyList(), 0);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.removeItem(productId, customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verify(mockCart).removeProduct(mockProduct);
            verify(cartMapper).toCartResponse(mockCart);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("When cart and product exist, should remove product via domain method without explicit save")
        void removeItem_WhenValidRequest_ShouldRemoveProductAndReturnMappedResponse() {
            // Given
            Long customerId = 1L;
            Long productId = 10L;

            Cart mockCart = mock(Cart.class);
            Product mockProduct = mock(Product.class);

            CartResponse expectedResponse = new CartResponse(42L, Collections.emptyList(), 0);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(productRepository.findById(productId))
                    .willReturn(Optional.of(mockProduct));
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.removeItem(productId, customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(productRepository).findById(productId);
            verify(mockCart).removeProduct(mockProduct);
            verify(cartMapper).toCartResponse(mockCart);

            // JPA Dirty Checking doğrulaması: explicit save() çağrısı yapılmamalı!
            verify(cartRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("clearCart Tests")
    class ClearCartTests {

        @Test
        @DisplayName("When cart does not exist, should return empty CartResponse without clearing or mapping")
        void clearCart_WhenCartDoesNotExist_ShouldReturnEmptyResponseWithoutClearing() {
            // Given
            Long customerId = 1L;

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.empty());

            // When
            CartResponse actualResponse = cartService.clearCart(customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isNull();
            assertThat(actualResponse.items()).isEmpty();
            assertThat(actualResponse.totalItemCount()).isZero();

            verify(cartRepository).findByCustomer_Id(customerId);
            verifyNoInteractions(cartMapper);
            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("When cart exists, should invoke cart.clear and return mapped response without explicit save")
        void clearCart_WhenCartExists_ShouldClearCartAndReturnMappedResponse() {
            // Given
            Long customerId = 1L;
            Cart mockCart = mock(Cart.class);

            CartResponse expectedResponse = new CartResponse(42L, Collections.emptyList(), 0);

            given(cartRepository.findByCustomer_Id(customerId))
                    .willReturn(Optional.of(mockCart));
            given(cartMapper.toCartResponse(mockCart))
                    .willReturn(expectedResponse);

            // When
            CartResponse actualResponse = cartService.clearCart(customerId);

            // Then
            assertThat(actualResponse).isNotNull();
            assertThat(actualResponse.cartId()).isEqualTo(42L);
            assertThat(actualResponse.totalItemCount()).isZero();
            assertThat(actualResponse.items()).isEmpty();

            verify(cartRepository).findByCustomer_Id(customerId);
            verify(mockCart).clear();
            verify(cartMapper).toCartResponse(mockCart);

            // JPA Dirty Checking doğrulaması: explicit save() çağrısı yapılmamalı!
            verify(cartRepository, never()).save(any());
        }
    }
}