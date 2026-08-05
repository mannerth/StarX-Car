package org.mannerth.starxcar.order.pricing;

import org.mannerth.starxcar.order.PricingRuleStatus;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRulePageResponse;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;

/** Admin-only lifecycle management for versioned pricing rules and their full piecewise definitions. */
public interface AdminPricingRuleService {
    PricingRulePageResponse list(long page, long size, PricingRuleStatus status);

    PricingRuleResponse create(Long adminId, PricingRuleWriteRequest request);

    PricingRuleResponse get(Long ruleId);

    PricingRuleResponse updateDraft(Long adminId, Long ruleId, PricingRuleWriteRequest request);

    PricingRuleResponse activate(Long adminId, Long ruleId, PricingRuleActivationRequest request);

    PricingRuleResponse archive(Long adminId, Long ruleId);
}
