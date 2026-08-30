package org.mannerth.starxcar.order.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单详情；金额在 COMPLETED 前均为预估")
public record OrderDetailResponse(
        String orderNo,
        OrderStatus status,
        @Schema(description = "订单类型：INSTANT 即时单、SCHEDULED 预约单") OrderType orderType,
        @Schema(description = "预约上车时间；即时单为空", example = "2026-08-05 10:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime scheduledPickupTime,
        @Schema(description = "订单进入可抢单 PENDING 的时间；即时单等于创建时间", example = "2026-08-05 09:30:00")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime dispatchTime,
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
        @Schema(description = "乘客手机尾号（后四位）；司机开始服务前用于身份核对", example = "1234") String passengerPhoneTail,
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

