package com.example.ordersystem.service.impl;

import com.example.ordersystem.dto.response.ShipmentResponse;
import com.example.ordersystem.entity.Shipment;
import com.example.ordersystem.exception.ResourceNotFoundException;
import com.example.ordersystem.mapper.ShipmentMapper;
import com.example.ordersystem.repository.ShipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ShipmentServiceImplTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private Shipment mockShipment;

    @InjectMocks
    private ShipmentServiceImpl shipmentService;

    @Mock
    private ShipmentResponse mockShipmentResponse;

    @Mock
    private ShipmentMapper shipmentMapper;

    @Nested
    @DisplayName("getShipmentByOrderId Tests")
    class GetShipmentTests {

        @Test
        @DisplayName("Should return ShipmentResponse when shipment exists")
        void shouldReturnShipmentResponseWhenShipmentExists() {
            Long orderId = 100L;

            given(shipmentRepository.findByOrderId(orderId)).willReturn(Optional.of(mockShipment));
            given(shipmentMapper.toShipmentResponse(mockShipment)).willReturn(mockShipmentResponse);

            ShipmentResponse response = shipmentService.getShipmentByOrderId(orderId);

            assertThat(response).isNotNull().isEqualTo(mockShipmentResponse);

            verify(shipmentMapper).toShipmentResponse(mockShipment);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shipment does not exist")
        void shouldThrowResourceNotFoundExceptionWhenShipmentNotFound() {
            Long orderId = 100L;

            given(shipmentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.getShipmentByOrderId(orderId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("shipShipment Tests")
    class ShipShipmentTests {

        @Test
        @DisplayName("Should find shipment and call markAsShipped")
        void shouldDelegateToMarkAsShipped() {
            Long shipmentId = 50L;
            String trackingNumber = "TR123456";
            String carrier = "Yurtici";

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.of(mockShipment));
            given(shipmentMapper.toShipmentResponse(mockShipment)).willReturn(mockShipmentResponse);

            ShipmentResponse response = shipmentService.shipShipment(shipmentId, trackingNumber, carrier);

            assertThat(response).isNotNull().isEqualTo(mockShipmentResponse);

            verify(mockShipment).markAsShipped(trackingNumber, carrier);
            verify(shipmentMapper).toShipmentResponse(mockShipment);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shipment is not found")
        void shouldThrowResourceNotFoundException() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.shipShipment(shipmentId, "TR123", "Yurtici"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("moveShipmentToInTransit Tests")
    class InTransitTests {

        @Test
        @DisplayName("Should find shipment and call markAsInTransit")
        void shouldDelegateToMarkAsInTransit() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.of(mockShipment));

            shipmentService.moveShipmentToInTransit(shipmentId);

            verify(mockShipment).markAsInTransit();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shipment is not found")
        void shouldThrowResourceNotFoundException() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.moveShipmentToInTransit(shipmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deliverShipment Tests")
    class DeliverTests {

        @Test
        @DisplayName("Should find shipment and call markAsDelivered")
        void shouldDelegateToMarkAsDelivered() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.of(mockShipment));

            shipmentService.deliverShipment(shipmentId);

            verify(mockShipment).markAsDelivered();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shipment is not found")
        void shouldThrowResourceNotFoundException() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.deliverShipment(shipmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("cancelShipment Tests")
    class CancelTests {

        @Test
        @DisplayName("Should find shipment and call cancel")
        void shouldDelegateToCancel() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.of(mockShipment));

            shipmentService.cancelShipment(shipmentId);

            verify(mockShipment).cancel();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shipment is not found")
        void shouldThrowResourceNotFoundException() {
            Long shipmentId = 50L;

            given(shipmentRepository.findById(shipmentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> shipmentService.cancelShipment(shipmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
