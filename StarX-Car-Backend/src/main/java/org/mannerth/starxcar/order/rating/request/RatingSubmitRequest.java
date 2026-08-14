package org.mannerth.starxcar.order.rating.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "乘客提交司机评价请求；仅已完成订单可评价，一个订单只能评价一次")
public record RatingSubmitRequest(
        @Schema(description = "1-5 星整数评分", example = "5")
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分必须在 1-5 之间")
        @Max(value = 5, message = "评分必须在 1-5 之间")
        Integer rating,

        @Schema(description = "可选评价文字，最多 500 字", example = "司机服务态度很好，车内整洁")
        @Size(max = 500, message = "评价文字不能超过 500 字")
        String content
) {
}