package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;






/** Pure calculator for distance, driving-time and arrival-waiting piecewise tariffs. */
@Component
public class FareCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final PricingRuleValidator validator;

    public FareCalculator(PricingRuleValidator validator) {
        this.validator = validator;
    }

    public FareCalculation calculate(PricingRuleDefinition definition, long distanceM, long drivingDurationS,
                                     long waitingDurationS) {
        requireNonNegative(distanceM, "计费距离");
        requireNonNegative(drivingDurationS, "行程时长");
        requireNonNegative(waitingDurationS, "等待时长");
        validator.validate(definition);

        List<FareLine> lines = new ArrayList<>();
        List<FareLine> distanceLines = calculateTiers(definition, FareChargeType.DISTANCE, distanceM);
        List<FareLine> drivingLines = calculateTiers(definition, FareChargeType.DRIVING_TIME, drivingDurationS);
        List<FareLine> waitingLines = calculateTiers(definition, FareChargeType.WAITING_TIME, waitingDurationS);
        BigDecimal distanceFare = total(distanceLines);
        BigDecimal drivingFare = total(drivingLines);
        BigDecimal waitingFare = total(waitingLines);
        BigDecimal ruleBaseFare = money(definition.baseFare());
        BigDecimal rawTotal = ruleBaseFare.add(distanceFare).add(drivingFare).add(waitingFare);
        BigDecimal minimumFareAdjustment = definition.minimumFare().compareTo(rawTotal) > 0
                ? money(definition.minimumFare().subtract(rawTotal)) : ZERO;
        BigDecimal chargedBaseFare = ruleBaseFare.add(minimumFareAdjustment);
        lines.add(new FareLine(FareChargeType.BASE, null, null, null, 0, null, ruleBaseFare, 1, chargedBaseFare));
        lines.addAll(distanceLines);
        lines.addAll(drivingLines);
        lines.addAll(waitingLines);
        return new FareCalculation(chargedBaseFare, minimumFareAdjustment, distanceFare, drivingFare, waitingFare,
                rawTotal.add(minimumFareAdjustment), lines);
    }

    private List<FareLine> calculateTiers(PricingRuleDefinition definition, FareChargeType chargeType, long usage) {
        List<PricingTierDefinition> tiers = definition.tiers().stream()
                .filter(tier -> tier.chargeType() == chargeType)
                .sorted(Comparator.comparingInt(PricingTierDefinition::sortNo))
                .toList();
        List<FareLine> lines = new ArrayList<>();
        for (PricingTierDefinition tier : tiers) {
            long start = tier.rangeStart();
            if (chargeType == FareChargeType.DISTANCE) {
                start = Math.max(start, definition.includedDistanceM());
            }
            long end = tier.rangeEnd() == null ? usage : Math.min(usage, tier.rangeEnd());
            long actualUsage = end > start ? end - start : 0;
            if (actualUsage == 0) {
                continue;
            }
            long chargedUnits = chargedUnits(actualUsage, tier.unitSize());
            BigDecimal amount = money(tier.unitPrice().multiply(BigDecimal.valueOf(chargedUnits)));
            lines.add(new FareLine(chargeType, tier.id(), start, tier.rangeEnd(), actualUsage, tier.unitSize(),
                    money(tier.unitPrice()), chargedUnits, amount));
        }
        return lines;
    }

    private long chargedUnits(long usage, long unitSize) {
        return usage / unitSize + (usage % unitSize == 0 ? 0 : 1);
    }

    private BigDecimal total(List<FareLine> lines) {
        return lines.stream().map(FareLine::amount).reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " 不能为负数");
        }
    }
}