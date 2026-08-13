package org.mannerth.starxcar.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "开始服务请求；司机需输入乘客手机尾号（后四位）完成身份核对")
public record StartServiceRequest(
        @Schema(description = "乘客手机尾号（后四位数字）", example = "1234")
        @NotBlank(message = "乘客手机尾号不能为空")
        @Pattern(regexp = "\\d{4}", message = "乘客手机尾号必须为 4 位数字")
        String phoneTail
) {
}
