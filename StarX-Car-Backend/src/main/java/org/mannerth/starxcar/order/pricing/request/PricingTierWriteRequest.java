package org.mannerth.starxcar.order.pricing.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.mannerth.starxcar.order.FareChargeType;
import org.mannerth.starxcar.order.RoundingMode;

import java.math.BigDecimal;

@Schema(description = "计价规则的一个分段；距离单位为米，时间单位为秒，区间包含起点但不包含终点")
public record PricingTierWriteRequest(
        @NotNull @Schema(description = "收费类型", example = "DISTANCE") FareChargeType chargeType,
        @NotNull @Min(0) @Max(4_294_967_295L) @Schema(description = "累计区间起点", example = "2000") Long rangeStart,
        @Min(0) @Max(4_294_967_295L) @Schema(description = "累计区间终点；最后一个分段为 null", example = "12000") Long rangeEnd,
        @NotNull @Min(1) @Max(4_294_967_295L) @Schema(description = "计费单位大小，例如 1000 米或 60 秒", example = "1000") Long unitSize,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(description = "每单位价格，元", example = "3.00") BigDecimal unitPrice,
        @NotNull @Schema(description = "不足一个单位的取整方式；一期只支持 CEIL", example = "CEIL") RoundingMode roundingMode,
        @NotNull @Min(0) @Schema(description = "同一收费类型内的计算顺序", example = "20") Integer sortNo
) {
}
