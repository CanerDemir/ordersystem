package com.example.ordersystem.entity;

import com.example.ordersystem.enums.OrderStatus;
import com.example.ordersystem.exception.OrderCannotBePaidException;
import com.example.ordersystem.exception.OrderStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        order = new Order(
                OrderStatus.PENDING,
                customer,
                "5551234567",
                "John",
                "Doe",
                "john.doe@example.com",
                BigDecimal.valueOf(100.00)
        );
    }

    // #####################
    // markAsPaid() Tests
    // #####################
    @Nested
    @DisplayName("markAsPaid testleri")
    class MarkAsPaidTests {
        @Test
        @DisplayName("markAsPaid: PENDING durumundaki sipariş ödeme alındığında PAID olmalı ve paidAt zamanı set edilmeli")
        void markAsPaid_whenStatusIsPending_shouldTransitionToPaidAndSetPaidAt() {
            // Act
            order.markAsPaid();

            // Assert
            assertEquals(OrderStatus.PAID, order.getStatus());
            assertNotNull(order.getPaidAt());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING"})
        @DisplayName("markAsPaid: PENDING dışındaki durumlarda OrderCannotBePaidException fırlatmalı")
        void markAsPaid_whenStatusIsNotPending_shouldThrowOrderCannotBePaidException(OrderStatus invalidStatus) {
            // Arrange - Reflection ile order'ı test edilmek istenen geçersiz duruma getiriyoruz
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Act & Assert
            assertThrows(OrderCannotBePaidException.class, () -> order.markAsPaid());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING"})
        @DisplayName("markAsPaid: Başarısız geçiş denemesinde domain state'i değişmemeli")
        void markAsPaid_whenTransitionFails_shouldNotMutateState(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Eşleşebilecek mevcut paidAt veya durum takibi için bir ilk değer ataması
            Instant initialPaidAt = order.getPaidAt();

            // Act & Assert
            assertThrows(OrderCannotBePaidException.class, () -> order.markAsPaid());

            // Exception fırladıktan sonra durumların ve alanların korunduğunu doğruluyoruz
            assertEquals(invalidStatus, order.getStatus(), "Sipariş statüsü değişmemiş olmalı");
            assertEquals(initialPaidAt, order.getPaidAt(), "paidAt alanı değişmemiş olmalı");
        }
    }

    // #####################
    // startPreparing() Tests
    // #####################
    @Nested
    @DisplayName("startPreparing testleri")
    class StartPreparingTests {

        @Test
        @DisplayName("startPreparing: PAID durumundaki sipariş hazırlık aşamasına (PREPARING) geçebilmeli")
        void startPreparing_whenStatusIsPaid_shouldTransitionToPreparing() {
            // Arrange
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);

            // Act
            order.startPreparing();

            // Assert
            assertEquals(OrderStatus.PREPARING, order.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PAID"})
        @DisplayName("startPreparing: PAID dışındaki tüm durumlarda OrderStatusTransitionException fırlatmalı")
        void startPreparing_whenStatusIsNotPaid_shouldThrowOrderStatusTransitionException(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Act & Assert
            assertThrows(OrderStatusTransitionException.class, () -> order.startPreparing());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PAID"})
        @DisplayName("startPreparing: Başarısız geçişlerde state korunmalı ve doğru exception mesajı üretilmeli")
        void startPreparing_whenTransitionFails_shouldNotMutateStateAndVerifyExceptionMessage(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> order.startPreparing()
            );

            // 1. Domain state mutasyona uğramamalı, mevcut durum korunmalı
            assertEquals(invalidStatus, order.getStatus(), "Başarısız geçişte sipariş statüsü değişmemeli");

            // 2. Exception mesajı hem mevcut status'ü (from) hem de yanlış yazılmış bir target state bug'ını yakalamak için hedef status'ü (PREPARING) içermeli
            String exceptionMessage = exception.getMessage();
            assertNotNull(exceptionMessage);
            assertTrue(exceptionMessage.contains(invalidStatus.name()), "Exception mesajı kaynak statüyü içermeli");
            assertTrue(exceptionMessage.contains(OrderStatus.PREPARING.name()), "Exception mesajı hedef statüyü (PREPARING) içermeli");
        }
    }

    // #####################
    // markAsShipped() Tests
    // #####################
    @Nested
    @DisplayName("markAsShipped testleri")
    class MarkAsShippedTests {

        @Test
        @DisplayName("markAsShipped: PREPARING durumundaki sipariş kargolandığında (SHIPPED) olmalı ve shippedAt zamanı set edilmeli")
        void markAsShipped_whenStatusIsPreparing_shouldTransitionToShippedAndSetShippedAt() {
            // Arrange
            ReflectionTestUtils.setField(order, "status", OrderStatus.PREPARING);

            // Act
            order.markAsShipped();

            // Assert
            assertEquals(OrderStatus.SHIPPED, order.getStatus());
            assertNotNull(order.getShippedAt());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PREPARING"})
        @DisplayName("markAsShipped: PREPARING dışındaki tüm durumlarda OrderStatusTransitionException fırlatmalı")
        void markAsShipped_whenStatusIsNotPreparing_shouldThrowOrderStatusTransitionException(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Act & Assert
            assertThrows(OrderStatusTransitionException.class, () -> order.markAsShipped());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PREPARING"})
        @DisplayName("markAsShipped: Başarısız geçişlerde status ve shippedAt değişmemeli, exception mesajında from/to doğrulanmalı")
        void markAsShipped_whenTransitionFails_shouldNotMutateStateAndVerifyExceptionMessage(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);
            Instant initialShippedAt = order.getShippedAt();

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> order.markAsShipped()
            );

            // 1. State & Timestamp Invariant doğrulamaları
            assertEquals(invalidStatus, order.getStatus(), "Başarısız geçişte sipariş statüsü değişmemeli");
            assertEquals(initialShippedAt, order.getShippedAt(), "shippedAt alanı değişmemeli veya set edilmemeli");

            // 2. Target/Source State Bug kontrolü için Exception Message doğrulaması
            String exceptionMessage = exception.getMessage();
            assertNotNull(exceptionMessage);
            assertTrue(exceptionMessage.contains(invalidStatus.name()), "Exception mesajı kaynak statüyü içermeli");
            assertTrue(exceptionMessage.contains(OrderStatus.SHIPPED.name()), "Exception mesajı hedef statüyü (SHIPPED) içermeli");
        }
    }

    // #####################
    // markAsDelivered() Tests
    // #####################
    @Nested
    @DisplayName("markAsDelivered testleri")
    class MarkAsDeliveredTests {

        @Test
        @DisplayName("markAsDelivered: SHIPPED durumundaki sipariş teslim edildiğinde (DELIVERED) olmalı ve deliveredAt zamanı set edilmeli")
        void markAsDelivered_whenStatusIsShipped_shouldTransitionToDeliveredAndSetDeliveredAt() {
            // Arrange
            ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);

            // Act
            order.markAsDelivered();

            // Assert
            assertEquals(OrderStatus.DELIVERED, order.getStatus());
            assertNotNull(order.getDeliveredAt());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"SHIPPED"})
        @DisplayName("markAsDelivered: SHIPPED dışındaki tüm durumlarda OrderStatusTransitionException fırlatmalı")
        void markAsDelivered_whenStatusIsNotShipped_shouldThrowOrderStatusTransitionException(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);

            // Act & Assert
            assertThrows(OrderStatusTransitionException.class, () -> order.markAsDelivered());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"SHIPPED"})
        @DisplayName("markAsDelivered: Başarısız geçişlerde status ve deliveredAt değişmemeli, exception mesajında from/to doğrulanmalı")
        void markAsDelivered_whenTransitionFails_shouldNotMutateStateAndVerifyExceptionMessage(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);
            Instant initialDeliveredAt = order.getDeliveredAt();

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> order.markAsDelivered()
            );

            // 1. State & Timestamp Invariant doğrulamaları
            assertEquals(invalidStatus, order.getStatus(), "Başarısız geçişte sipariş statüsü değişmemeli");
            assertEquals(initialDeliveredAt, order.getDeliveredAt(), "deliveredAt alanı değişmemeli veya set edilmemeli");

            // 2. Target/Source State Bug kontrolü için Exception Message doğrulaması
            String exceptionMessage = exception.getMessage();
            assertNotNull(exceptionMessage);
            assertTrue(exceptionMessage.contains(invalidStatus.name()), "Exception mesajı kaynak statüyü içermeli");
            assertTrue(exceptionMessage.contains(OrderStatus.DELIVERED.name()), "Exception mesajı hedef statüyü (DELIVERED) içermeli");
        }
    }

    // #####################
    // refund() Tests
    // #####################
    @Nested
    @DisplayName("refund testleri")
    class RefundTestleri {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED"})
        @DisplayName("refund: PAID, PREPARING, SHIPPED veya DELIVERED durumundaki sipariş iade edilebilir (REFUNDED) olmalı ve refundedAt set edilmeli")
        void refund_whenStatusIsValidForRefund_shouldTransitionToRefundedAndSetRefundedAt(OrderStatus validStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", validStatus);

            // Act
            order.refund();

            // Assert
            assertEquals(OrderStatus.REFUNDED, order.getStatus());
            assertNotNull(order.getRefundedAt());
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED"})
        @DisplayName("refund: PAID, PREPARING, SHIPPED veya DELIVERED dışındaki durumlarda OrderStatusTransitionException fırlatmalı ve state korunmalı")
        void refund_whenStatusIsInvalidForRefund_shouldThrowExceptionAndNotMutateState(OrderStatus invalidStatus) {
            // Arrange
            ReflectionTestUtils.setField(order, "status", invalidStatus);
            Instant initialRefundedAt = order.getRefundedAt();

            // Act & Assert
            OrderStatusTransitionException exception = assertThrows(
                    OrderStatusTransitionException.class,
                    () -> order.refund()
            );

            // 1. State & Timestamp Invariant doğrulamaları
            assertEquals(invalidStatus, order.getStatus(), "Başarısız geçişte sipariş statüsü değişmemeli");
            assertEquals(initialRefundedAt, order.getRefundedAt(), "refundedAt alanı değişmemeli veya set edilmemeli");

            // 2. Target/Source State Bug kontrolü için Exception Message doğrulaması
            String exceptionMessage = exception.getMessage();
            assertNotNull(exceptionMessage);
            assertTrue(exceptionMessage.contains(invalidStatus.name()), "Exception mesajı kaynak statüyü içermeli");
            assertTrue(exceptionMessage.contains(OrderStatus.REFUNDED.name()), "Exception mesajı hedef statüyü (REFUNDED) içermeli");
        }
    }
}