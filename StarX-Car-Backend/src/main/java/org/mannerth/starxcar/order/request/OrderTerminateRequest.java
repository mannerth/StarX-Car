package org.mannerth.starxcar.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "服务中单方结束订单请求")
public record OrderTerminateRequest(
        @NotBlank @Size(max = 40) @Schema(description = "标准化结束原因", example = "PASSENGER_REQUEST") String reasonCode,
        @Size(max = 500) @Schema(description = "补充说明", example = "乘客临时要求就近结束") String reasonDetail
) {
}
