package org.mannerth.starxcar.order.pricing.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;






@Schema(description = "计价规则分页结果")
public record PricingRulePageResponse(long page, long size, long total, List<PricingRuleResponse> records) {
    public PricingRulePageResponse {
        records = List.copyOf(records);
    }
}