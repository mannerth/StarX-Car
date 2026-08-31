package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;




@Schema(description = "订单分页结果")
public record OrderPageResponse(
        @Schema(description = "页码，从 1 开始") long page,
        @Schema(description = "每页数量") long size,
        @Schema(description = "总记录数") long total,
        @Schema(description = "订单列表") List<OrderSummaryResponse> records
) {
    public OrderPageResponse {
        records = List.copyOf(records);
    }
}