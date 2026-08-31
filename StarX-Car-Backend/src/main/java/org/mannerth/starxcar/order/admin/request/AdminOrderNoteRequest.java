package org.mannerth.starxcar.order.admin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;






@Schema(description = "管理员运营备注")
public record AdminOrderNoteRequest(
        @NotBlank @Size(max = 500)
        @Schema(description = "追加到订单的运营备注", example = "已联系司机，请在预约时间前到达上车点") String content
) {
}