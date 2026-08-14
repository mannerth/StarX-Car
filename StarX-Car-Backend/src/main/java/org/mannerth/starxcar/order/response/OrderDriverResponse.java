package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "订单已接单司机的展示资料；完整手机号供乘客拨打联系司机，其余为展示字段")
public record OrderDriverResponse(
        @Schema(description = "司机 ID") Long driverId,
        @Schema(description = "司机完整手机号；仅用于拨号联系，不应直接展示", example = "13800138000") String phoneNumber,
        @Schema(description = "司机脱敏手机号", example = "138****0000") String displayPhone,
        @Schema(description = "司机评分（1.0-5.0），无评价时为 5.0", example = "4.8") BigDecimal rating,
        @Schema(description = "接单车辆车牌号", example = "京B12345") String carNumber,
        @Schema(description = "接单车辆品牌", example = "大众") String carBrand,
        @Schema(description = "接单车辆颜色", example = "黑色") String carColor,
        @Schema(description = "接单车辆车型", example = "economy") String carType
) {
}