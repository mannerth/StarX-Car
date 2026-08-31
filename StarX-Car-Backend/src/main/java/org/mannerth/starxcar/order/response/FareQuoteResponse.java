package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;




@Schema(description = "订单预估或快照金额")
public record FareQuoteResponse(
        @Schema(description = "基础费，已包含最低消费调整", example = "5.00") BigDecimal baseFare,
        @Schema(description = "距离费", example = "31.20") BigDecimal distanceFare,
        @Schema(description = "按预计时长计算的行程时间费", example = "6.50") BigDecimal drivingTimeFare,
        @Schema(description = "等待费；报价阶段固定为 0", example = "0.00") BigDecimal waitingFare,
        @Schema(description = "预估总价", example = "42.70") BigDecimal totalFare
) {
}