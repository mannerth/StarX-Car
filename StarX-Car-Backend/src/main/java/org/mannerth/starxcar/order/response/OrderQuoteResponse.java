package org.mannerth.starxcar.order.response;

import org.mannerth.starxcar.order.domain.OrderType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;




@Schema(description = "叫车服务端预估结果（即时/预约）")
public record OrderQuoteResponse(
        @Schema(description = "采用的计价规则 ID", example = "1") Long pricingRuleId,
        @Schema(description = "采用的计价规则版本", example = "1") int pricingRuleVersion,
        @Schema(description = "高德驾车规划距离，单位米", example = "12001") long estimatedDistanceM,
        @Schema(description = "高德驾车规划时长，单位秒", example = "1801") long estimatedDurationS,
        @Schema(description = "预估金额；下单时会重新规划并重新计算") FareQuoteResponse fare,
        @Schema(description = "订单类型", example = "INSTANT") OrderType orderType,
        @Schema(description = "预约上车时间；即时单为空", example = "2026-08-05 10:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime scheduledPickupTime
) {
}