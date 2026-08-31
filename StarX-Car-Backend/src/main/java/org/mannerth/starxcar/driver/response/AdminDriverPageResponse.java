package org.mannerth.starxcar.driver.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;






@Schema(description = "后台司机审核分页结果")
public record AdminDriverPageResponse(
        @Schema(description = "当前页，从 1 开始", example = "1") long page,
        @Schema(description = "每页数量", example = "10") long size,
        @Schema(description = "匹配的司机总数", example = "12") long total,
        List<AdminDriverResponse> records
) {
    public AdminDriverPageResponse {
        records = List.copyOf(records);
    }
}