package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;




class FareCalculatorTests {
    private final FareCalculator calculator = new FareCalculator(new PricingRuleValidator());

    @Test
    void calculatesDefaultDistanceBreakpointsWithoutChargingIncludedDistanceTwice() {
        PricingRuleDefinition rule = PricingFixtures.defaultRule();

        assertCalculation(calculator.calculate(rule, 2_000, 0, 0), "5.00", "0.00", "0.00", "0.00", "5.00");
        assertCalculation(calculator.calculate(rule, 2_001, 0, 0), "5.00", "3.00", "0.00", "0.00", "8.00");
        assertCalculation(calculator.calculate(rule, 12_000, 0, 0), "5.00", "30.00", "0.00", "0.00", "35.00");
        assertCalculation(calculator.calculate(rule, 12_001, 0, 0), "5.00", "31.20", "0.00", "0.00", "36.20");
    }

    @Test
    void calculatesDrivingAndWaitingBreakpointsWithFreeWaitingAuditLine() {
        PricingRuleDefinition rule = PricingFixtures.defaultRule();

        assertCalculation(calculator.calculate(rule, 0, 1_800, 300), "5.00", "0.00", "6.00", "0.00", "11.00");
        FareCalculation afterThreshold = calculator.calculate(rule, 0, 1_801, 301);
        assertCalculation(afterThreshold, "5.00", "0.00", "6.50", "0.50", "12.00");
        assertThat(afterThreshold.lines()).anySatisfy(line -> {
            assertThat(line.chargeType()).isEqualTo(FareChargeType.WAITING_TIME);
            assertThat(line.tierId()).isEqualTo(31L);
            assertThat(line.actualUsage()).isEqualTo(300);
            assertThat(line.chargedUnits()).isEqualTo(5);
            assertThat(line.amount()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void appliesMinimumFareAsAnExplicitBaseAdjustment() {
        PricingRuleDefinition baseRule = PricingFixtures.defaultRule();
        PricingRuleDefinition minimumRule = new PricingRuleDefinition(2L, "最低消费测试", 1,
                PricingRuleStatus.active, false, new BigDecimal("1.00"), 0, new BigDecimal("5.00"), new BigDecimal("5.00"),
                baseRule.tiers().stream().map(tier -> new PricingTierDefinition(tier.id(), tier.chargeType(),
                        tier.rangeStart(), tier.rangeEnd(), tier.unitSize(), BigDecimal.ZERO,
                        tier.roundingMode(), tier.sortNo())).toList());

        FareCalculation calculation = calculator.calculate(minimumRule, 0, 0, 0);

        assertThat(calculation.baseFare()).isEqualByComparingTo("5.00");
        assertThat(calculation.minimumFareAdjustment()).isEqualByComparingTo("4.00");
        assertThat(calculation.totalFare()).isEqualByComparingTo("5.00");
        assertThat(calculation.lines().stream().map(FareLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(calculation.totalFare());
    }

    @Test
    void rejectsGapsAndOverlapsInPiecewiseFunctions() {
        PricingRuleDefinition rule = PricingFixtures.defaultRule();
        List<PricingTierDefinition> invalidTiers = rule.tiers().stream()
                .map(tier -> tier.id().equals(12L)
                        ? PricingFixtures.tier(12L, FareChargeType.DISTANCE, 2_001, 12_000L, 1_000, "3.00", 20)
                        : tier)
                .toList();
        PricingRuleDefinition invalidRule = new PricingRuleDefinition(rule.id(), rule.name(), rule.version(),
                rule.status(), rule.defaultRule(), rule.baseFare(), rule.includedDistanceM(), rule.minimumFare(), rule.arrivalCancelFee(),
                invalidTiers);

        assertThatThrownBy(() -> calculator.calculate(invalidRule, 2_001, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空洞或重叠");
    }

    private void assertCalculation(FareCalculation calculation, String baseFare, String distanceFare,
                                   String drivingTimeFare, String waitingFare, String totalFare) {
        assertThat(calculation.baseFare()).isEqualByComparingTo(baseFare);
        assertThat(calculation.distanceFare()).isEqualByComparingTo(distanceFare);
        assertThat(calculation.drivingTimeFare()).isEqualByComparingTo(drivingTimeFare);
        assertThat(calculation.waitingFare()).isEqualByComparingTo(waitingFare);
        assertThat(calculation.totalFare()).isEqualByComparingTo(totalFare);
    }
}
