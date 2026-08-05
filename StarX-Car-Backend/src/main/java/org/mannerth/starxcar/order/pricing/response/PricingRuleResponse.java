package org.mannerth.starxcar.order.pricing.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.order.PricingRuleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "计价规则及其完整分段")
public record PricingRuleResponse(
        Long id,
        String name,
        Integer version,
        PricingRuleStatus status,
        @Schema(description = "是否当前默认规则") boolean defaultRule,
        BigDecimal baseFare,
        Long includedDistanceM,
        BigDecimal minimumFare,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        List<PricingTierResponse> tiers
) {
    public PricingRuleResponse {
        tiers = List.copyOf(tiers);
    }
}
