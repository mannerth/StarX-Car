package org.mannerth.starxcar.driver.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "司机可主动设置的出车状态")
public record DriverWorkStatusRequest(
        @Schema(description = "仅允许 online 或 offline；busy 由接单服务维护", example = "online")
        @Pattern(regexp = "online|offline", message = "状态只能为 online 或 offline") String status
) {
}
