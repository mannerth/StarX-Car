package org.mannerth.starxcar.order.rating.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "评价列表项")
public record RatingSummaryResponse(
        @Schema(description = "被评价订单号") String orderNo,
        @Schema(description = "评分 1-5 星") Integer rating,
        @Schema(description = "评价文字") String content,
        @Schema(description = "评价提交时间") LocalDateTime createdAt
) {
}