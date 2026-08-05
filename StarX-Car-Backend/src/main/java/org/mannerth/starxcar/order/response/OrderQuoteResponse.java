package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "即时叫车服务端预估结果")
public record OrderQuoteResponse(
        @Schema(description = "采用的计价规则 ID", example = "1") Long pricingRuleId,
        @Schema(description = "采用的计价规则版本", example = "1") int pricingRuleVersion,
        @Schema(description = "高德驾车规划距离，单位米", example = "12001") long estimatedDistanceM,
        @Schema(description = "高德驾车规划时长，单位秒", example = "1801") long estimatedDurationS,
        @Schema(description = "预估金额；下单时会重新规划并重新计算") FareQuoteResponse fare
) {
}
