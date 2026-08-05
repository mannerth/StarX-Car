package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单详情；金额在 COMPLETED 前均为预估")
public record OrderDetailResponse(
        String orderNo,
        OrderStatus status,
        String pickupPoiId,
        String pickupName,
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String destinationPoiId,
        String destinationName,
        String destinationAddress,
        BigDecimal destinationLat,
        BigDecimal destinationLng,
        long estimatedDistanceM,
        long estimatedDurationS,
        long billingDistanceM,
        Long actualDurationS,
        Long waitingDurationS,
        FareQuoteResponse fare,
        boolean estimatedFare,
        OrderDriverResponse driver,
        LocalDateTime createdTime,
        LocalDateTime acceptedTime,
        LocalDateTime driverArrivedTime,
        LocalDateTime serviceStartedTime,
        LocalDateTime completedTime,
        LocalDateTime cancelledTime,
        String cancelledBy,
        String cancelReason,
        List<OrderEventResponse> events
) {
    public OrderDetailResponse {
        events = List.copyOf(events);
    }
}
