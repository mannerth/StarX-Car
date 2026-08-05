package org.mannerth.starxcar.order.pricing;

public interface PricingRuleService {

    PricingRuleDefinition getDefaultActiveRule();

    PricingRuleDefinition getByIdAndVersion(Long ruleId, int version);
}
