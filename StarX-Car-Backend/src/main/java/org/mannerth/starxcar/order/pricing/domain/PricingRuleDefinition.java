package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import java.math.BigDecimal;
import java.util.List;






/** Immutable price-rule snapshot used by quoting and settlement. */
public record PricingRuleDefinition(
        Long id,
        String name,
        int version,
        PricingRuleStatus status,
        boolean defaultRule,
        BigDecimal baseFare,
        long includedDistanceM,
        BigDecimal minimumFare,
        BigDecimal arrivalCancelFee,
        List<PricingTierDefinition> tiers
) {
    public PricingRuleDefinition {
        tiers = List.copyOf(tiers);
    }
}
