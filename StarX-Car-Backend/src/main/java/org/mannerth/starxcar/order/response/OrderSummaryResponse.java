package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "订单列表项")
public record OrderSummaryResponse(
        @Schema(description = "订单号") String orderNo,
        @Schema(description = "订单状态") OrderStatus status,
        @Schema(description = "上车点名称") String pickupName,
        @Schema(description = "目的地名称") String destinationName,
        @Schema(description = "当前预估或结算总价") BigDecimal totalFare,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "true 表示金额尚未最终结算") boolean estimatedFare
) {
}
