package org.mannerth.starxcar.order.response;

import org.mannerth.starxcar.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;




@Schema(description = "订单完成与最终结算结果")
public record OrderCompletionResponse(
        @Schema(description = "对外订单号") String orderNo,
        @Schema(description = "订单完成状态", example = "COMPLETED") OrderStatus status,
        @Schema(description = "服务端完成时间") LocalDateTime completedTime,
        @Schema(description = "实际服务秒数") long actualDurationS,
        @Schema(description = "司机到达到开始服务的等待秒数") long waitingDurationS,
        @Schema(description = "按订单规则快照结算的最终金额") FareQuoteResponse fare
) {
}