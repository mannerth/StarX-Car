package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import org.mannerth.starxcar.order.domain.RoundingMode;
import java.math.BigDecimal;
import java.util.List;




public final class PricingFixtures {
    private PricingFixtures() {
    }

    public static PricingRuleDefinition defaultRule() {
        return new PricingRuleDefinition(1L, "默认即时叫车规则", 1, PricingRuleStatus.active, true,
                new BigDecimal("5.00"), 2_000, new BigDecimal("5.00"), List.of(
                tier(11L, FareChargeType.DISTANCE, 0, 2_000L, 1_000, "0.00", 10),
                tier(12L, FareChargeType.DISTANCE, 2_000, 12_000L, 1_000, "3.00", 20),
                tier(13L, FareChargeType.DISTANCE, 12_000, null, 1_000, "1.20", 30),
                tier(21L, FareChargeType.DRIVING_TIME, 0, 1_800L, 60, "0.20", 10),
                tier(22L, FareChargeType.DRIVING_TIME, 1_800, null, 60, "0.50", 20),
                tier(31L, FareChargeType.WAITING_TIME, 0, 300L, 60, "0.00", 10),
                tier(32L, FareChargeType.WAITING_TIME, 300, null, 60, "0.50", 20)
        ));
    }

    public static PricingTierDefinition tier(Long id, FareChargeType chargeType, long rangeStart, Long rangeEnd,
                                      long unitSize, String unitPrice, int sortNo) {
        return new PricingTierDefinition(id, chargeType, rangeStart, rangeEnd, unitSize,
                new BigDecimal(unitPrice), RoundingMode.CEIL, sortNo);
    }

    public static PricingRule ruleEntity() {
        PricingRule rule = new PricingRule();
        rule.setId(1L);
        rule.setName("默认即时叫车规则");
        rule.setVersion(1);
        rule.setStatus(PricingRuleStatus.active);
        rule.setDefaultRule(true);
        rule.setBaseFare(new BigDecimal("5.00"));
        rule.setIncludedDistanceM(2_000L);
        rule.setMinimumFare(new BigDecimal("5.00"));
        return rule;
    }

    public static PricingRuleTier tierEntity(PricingTierDefinition definition) {
        PricingRuleTier tier = new PricingRuleTier();
        tier.setId(definition.id());
        tier.setRuleId(1L);
        tier.setChargeType(definition.chargeType());
        tier.setRangeStart(definition.rangeStart());
        tier.setRangeEnd(definition.rangeEnd());
        tier.setUnitSize(definition.unitSize());
        tier.setUnitPrice(definition.unitPrice());
        tier.setRoundingMode(definition.roundingMode());
        tier.setSortNo(definition.sortNo());
        return tier;
    }
}