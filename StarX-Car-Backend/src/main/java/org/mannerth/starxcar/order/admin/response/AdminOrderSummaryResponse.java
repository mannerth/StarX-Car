package org.mannerth.starxcar.order.admin.response;

import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;






@Schema(description = "管理端订单列表项")
public record AdminOrderSummaryResponse(
        String orderNo, OrderType orderType, OrderStatus status,
        String pickupName, String destinationName,
        String passengerName, String passengerDisplayPhone,
        String driverName, String driverDisplayPhone,
        @Schema(description = "预约上车时间；即时单为空") LocalDateTime scheduledPickupTime,
        LocalDateTime dispatchTime, LocalDateTime createdTime, LocalDateTime acceptedTime,
        LocalDateTime completedTime, BigDecimal totalFare, boolean estimatedFare
) {
}