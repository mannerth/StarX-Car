package org.mannerth.starxcar.order.rating.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;




@Schema(description = "评价分页结果")
public record RatingPageResponse(
        @Schema(description = "页码，从 1 开始") long page,
        @Schema(description = "每页数量") long size,
        @Schema(description = "总记录数") long total,
        @Schema(description = "评价列表") List<RatingSummaryResponse> records
) {
    public RatingPageResponse {
        records = List.copyOf(records);
    }
}