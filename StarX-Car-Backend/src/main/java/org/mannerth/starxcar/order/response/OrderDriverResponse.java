package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "订单已接单司机的最小展示资料")
public record OrderDriverResponse(
        @Schema(description = "司机 ID") Long driverId,
        @Schema(description = "司机脱敏手机号", example = "138****1234") String displayPhone,
        @Schema(description = "接单车辆车牌号") String carNumber
) {
}
