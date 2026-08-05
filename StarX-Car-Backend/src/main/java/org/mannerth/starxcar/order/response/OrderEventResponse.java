package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.OrderEventType;
import org.mannerth.starxcar.order.OrderOperatorType;
import org.mannerth.starxcar.order.OrderStatus;

import java.time.LocalDateTime;

@Schema(description = "订单状态审计事件")
public record OrderEventResponse(
        @Schema(description = "事件类型") OrderEventType eventType,
        @Schema(description = "变更前状态") OrderStatus fromStatus,
        @Schema(description = "变更后状态") OrderStatus toStatus,
        @Schema(description = "操作者类型") OrderOperatorType operatorType,
        @Schema(description = "事件时间") LocalDateTime createdTime,
        @Schema(description = "受控备注") String remark
) {
}
