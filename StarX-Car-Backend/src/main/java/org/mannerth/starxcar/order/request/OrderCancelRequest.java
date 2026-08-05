package org.mannerth.starxcar.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "订单取消请求")
public record OrderCancelRequest(
        @Schema(description = "可选取消原因；空白内容不会保存", example = "行程计划变更")
        @Size(max = 255) String reason
) {
}
