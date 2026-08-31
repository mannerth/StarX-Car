package org.mannerth.starxcar.order.response;

import org.mannerth.starxcar.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;




@Schema(description = "即时订单创建结果")
public record OrderCreatedResponse(
        @Schema(description = "对外订单号", example = "O2026080423595900000") String orderNo,
        @Schema(description = "初始订单状态", example = "PENDING") OrderStatus status,
        @Schema(description = "下单时锁定的路线与计费规则预估") OrderQuoteResponse quote
) {
}