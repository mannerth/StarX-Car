package org.mannerth.starxcar.order.pricing.request;

import io.swagger.v3.oas.annotations.media.Schema;






@Schema(description = "激活计价规则的选项")
public record PricingRuleActivationRequest(
        @Schema(description = "是否同时设为当前默认规则；未提供时为 false", example = "true") Boolean setAsDefault
) {
    public boolean shouldSetAsDefault() {
        return Boolean.TRUE.equals(setAsDefault);
    }
}