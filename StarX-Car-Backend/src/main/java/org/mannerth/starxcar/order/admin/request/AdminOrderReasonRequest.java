package org.mannerth.starxcar.order.admin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;






@Schema(description = "管理员订单干预原因")
public record AdminOrderReasonRequest(
        @NotBlank @Size(max = 500)
        @Schema(description = "必须填写的运营原因", example = "乘客电话确认取消") String reason
) {
}