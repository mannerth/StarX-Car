package org.mannerth.starxcar.order.rating.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "订单评价查询结果；未评价时 rated 为 false，其余字段为空")
public record OrderRatingResponse(
        @Schema(description = "是否已评价") boolean rated,
        @Schema(description = "评分 1-5 星") Integer rating,
        @Schema(description = "评价文字") String content,
        @Schema(description = "评价提交时间") LocalDateTime createdAt
) {
}