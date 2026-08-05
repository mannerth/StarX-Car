package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderStatus;

import java.time.LocalDateTime;

@Schema(description = "订单状态操作结果")
public record OrderStatusTransitionResponse(
        @Schema(description = "对外订单号", example = "O2026080500000000000") String orderNo,
        @Schema(description = "操作后的订单状态") OrderStatus status,
        @Schema(description = "服务端操作时间", example = "2026-08-05 10:05:00") LocalDateTime operationTime,
        @Schema(description = "仅开始服务时返回；司机到达到开始服务的等待秒数", example = "300") Long waitingDurationS
) {
}
