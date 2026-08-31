package org.mannerth.starxcar.order.pricing.application;

import org.mannerth.starxcar.order.pricing.domain.PricingRuleDefinition;






public interface PricingRuleService {

    PricingRuleDefinition getDefaultActiveRule();

    PricingRuleDefinition getByIdAndVersion(Long ruleId, int version);
}