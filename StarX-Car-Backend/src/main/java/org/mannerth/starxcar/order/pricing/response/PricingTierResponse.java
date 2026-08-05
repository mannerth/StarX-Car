package org.mannerth.starxcar.order.pricing.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.FareChargeType;
import org.mannerth.starxcar.order.RoundingMode;

import java.math.BigDecimal;

@Schema(description = "已保存的计价分段")
public record PricingTierResponse(
        @Schema(description = "分段 ID") Long id,
        FareChargeType chargeType,
        @Schema(description = "区间起点") Long rangeStart,
        @Schema(description = "区间终点；null 表示无上限") Long rangeEnd,
        @Schema(description = "计费单位大小") Long unitSize,
        @Schema(description = "每单位价格，元") BigDecimal unitPrice,
        RoundingMode roundingMode,
        @Schema(description = "计算顺序") Integer sortNo
) {
}
