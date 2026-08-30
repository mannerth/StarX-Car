package org.mannerth.starxcar.order.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "订单列表项")
public record OrderSummaryResponse(
        @Schema(description = "订单号") String orderNo,
        @Schema(description = "订单状态") OrderStatus status,
        @Schema(description = "订单类型：INSTANT 即时单、SCHEDULED 预约单") OrderType orderType,
        @Schema(description = "预约上车时间；即时单为空", example = "2026-08-05 10:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime scheduledPickupTime,
        @Schema(description = "上车点名称") String pickupName,
        @Schema(description = "目的地名称") String destinationName,
        @Schema(description = "当前预估或结算总价") BigDecimal totalFare,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "true 表示金额尚未最终结算") boolean estimatedFare
) {
}
