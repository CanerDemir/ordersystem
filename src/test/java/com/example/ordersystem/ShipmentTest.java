package com.example.ordersystem;

import com.example.ordersystem.entity.Order;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.enums.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class ShipmentTest {

    @Mock
    private Order mockOrder;

    @Nested
    @DisplayName("Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("createReady(order) should initialize shipment with READY status and correct order reference")
        void shouldCreateReadyShipment() {
            Shipment shipment = Shipment.createReady(mockOrder);

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
            assertThat(shipment.getOrder()).isSameAs(mockOrder);
            assertThat(shipment.getShippedAt()).isNull();
            assertThat(shipment.getDeliveredAt()).isNull();
            assertThat(shipment.getTrackingNumber()).isNull();
            assertThat(shipment.getCarrier()).isNull();
        }

        @Test
        @DisplayName("createReady(null) should throw IllegalArgumentException")
        void shouldThrowExceptionWhenOrderIsNull() {
            assertThatThrownBy(() -> Shipment.createReady(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Successful State Transitions & Timestamps")
    class SuccessfulTransitionsTests {

        @Test
        @DisplayName("1. READY -> SHIPPED transition should set trackingNumber, carrier, status and shippedAt timestamp")
        void shouldTransitionFromReadyToShipped() {
            Shipment shipment = Shipment.createReady(mockOrder);

            shipment.markAsShipped("TR123456", "Yurtici");

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
            assertThat(shipment.getTrackingNumber()).isEqualTo("TR123456");
            assertThat(shipment.getCarrier()).isEqualTo("Yurtici");
            assertThat(shipment.getShippedAt()).isNotNull();
            assertThat(shipment.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("2. SHIPPED -> IN_TRANSIT transition should update status and preserve shippedAt timestamp")
        void shouldTransitionFromShippedToInTransit() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.markAsShipped("TR123456", "Yurtici");
            var initialShippedAt = shipment.getShippedAt();

            shipment.markAsInTransit();

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
            assertThat(shipment.getShippedAt()).isEqualTo(initialShippedAt); // Preserved
            assertThat(shipment.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("3. IN_TRANSIT -> DELIVERED transition should update status, set deliveredAt and preserve shippedAt timestamp")
        void shouldTransitionFromInTransitToDelivered() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.markAsShipped("TR123456", "Yurtici");
            var initialShippedAt = shipment.getShippedAt();
            shipment.markAsInTransit();

            shipment.markAsDelivered();

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(shipment.getShippedAt()).isEqualTo(initialShippedAt); // Preserved invariant
            assertThat(shipment.getDeliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("4. READY -> CANCELLED transition should set status to CANCELLED and leave timestamps null")
        void shouldTransitionFromReadyToCancelled() {
            Shipment shipment = Shipment.createReady(mockOrder);

            shipment.cancel();

            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(shipment.getShippedAt()).isNull();
            assertThat(shipment.getDeliveredAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitionsTests {

        @Test
        @DisplayName("READY -> IN_TRANSIT is invalid")
        void shouldNotTransitionFromReadyToInTransit() {
            Shipment shipment = Shipment.createReady(mockOrder);

            assertThatThrownBy(shipment::markAsInTransit)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("READY -> DELIVERED is invalid")
        void shouldNotTransitionFromReadyToDelivered() {
            Shipment shipment = Shipment.createReady(mockOrder);

            assertThatThrownBy(shipment::markAsDelivered)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("SHIPPED -> DELIVERED is invalid")
        void shouldNotTransitionFromShippedToDelivered() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.markAsShipped("TR123456", "Yurtici");

            assertThatThrownBy(shipment::markAsDelivered)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("IN_TRANSIT -> SHIPPED is invalid")
        void shouldNotTransitionFromInTransitToShipped() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.markAsShipped("TR123456", "Yurtici");
            shipment.markAsInTransit();

            assertThatThrownBy(() -> shipment.markAsShipped("TR999", "Aras"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("DELIVERED -> Any transition is invalid")
        void shouldNotTransitionFromDeliveredToAnyState() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.markAsShipped("TR123456", "Yurtici");
            shipment.markAsInTransit();
            shipment.markAsDelivered();

            assertThatThrownBy(() -> shipment.markAsShipped("TR999", "Aras"))
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(shipment::markAsInTransit)
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(shipment::markAsDelivered)
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(shipment::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED -> Any transition is invalid")
        void shouldNotTransitionFromCancelledToAnyState() {
            Shipment shipment = Shipment.createReady(mockOrder);
            shipment.cancel();

            assertThatThrownBy(() -> shipment.markAsShipped("TR123456", "Yurtici"))
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(shipment::markAsInTransit)
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(shipment::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("SHIPPED -> CANCELLED / IN_TRANSIT -> CANCELLED are invalid in current version")
        void shouldNotCancelIfAlreadyShippedOrInTransit() {
            Shipment shipmentShipped = Shipment.createReady(mockOrder);
            shipmentShipped.markAsShipped("TR123456", "Yurtici");

            assertThatThrownBy(shipmentShipped::cancel)
                    .isInstanceOf(IllegalStateException.class);

            Shipment shipmentTransit = Shipment.createReady(mockOrder);
            shipmentTransit.markAsShipped("TR123456", "Yurtici");
            shipmentTransit.markAsInTransit();

            assertThatThrownBy(shipmentTransit::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Failed transition should not mutate domain state or timestamps (Side-effect free on failure)")
        void failedTransitionShouldNotChangeState() {
            // Given
            Shipment shipment = Shipment.createReady(mockOrder);

            // When & Then (Exception assertion)
            assertThatThrownBy(shipment::markAsInTransit)
                    .isInstanceOf(IllegalStateException.class);

            // Assert State Integrity (Exception sonrasında state tam olarak ilk halini korumalı)
            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
            assertThat(shipment.getTrackingNumber()).isNull();
            assertThat(shipment.getCarrier()).isNull();
            assertThat(shipment.getShippedAt()).isNull();
            assertThat(shipment.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("Failed validation during markAsShipped should not update status or set timestamps")
        void failedValidationShouldNotMutateState() {
            // Given
            Shipment shipment = Shipment.createReady(mockOrder);

            // When & Then (Invalid tracking number)
            assertThatThrownBy(() -> shipment.markAsShipped("   ", "Yurtici"))
                    .isInstanceOf(IllegalArgumentException.class);

            // Assert State Integrity
            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
            assertThat(shipment.getTrackingNumber()).isNull();
            assertThat(shipment.getCarrier()).isNull();
            assertThat(shipment.getShippedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Validation Rules for SHIPPED status")
    class ValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("markAsShipped should throw IllegalArgumentException when trackingNumber is null, empty or blank")
        void shouldThrowExceptionWhenTrackingNumberIsInvalid(String invalidTrackingNumber) {
            Shipment shipment = Shipment.createReady(mockOrder);

            assertThatThrownBy(() -> shipment.markAsShipped(invalidTrackingNumber, "Yurtici"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("markAsShipped should throw IllegalArgumentException when carrier is null, empty or blank")
        void shouldThrowExceptionWhenCarrierIsInvalid(String invalidCarrier) {
            Shipment shipment = Shipment.createReady(mockOrder);

            assertThatThrownBy(() -> shipment.markAsShipped("TR123456", invalidCarrier))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
