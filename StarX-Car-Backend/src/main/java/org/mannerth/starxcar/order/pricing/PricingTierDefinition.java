package org.mannerth.starxcar.order.pricing;

import org.mannerth.starxcar.order.FareChargeType;
import org.mannerth.starxcar.order.RoundingMode;

import java.math.BigDecimal;

/** Immutable pricing tier loaded from pricing_rule_tier. Distance uses meters; time uses seconds. */
public record PricingTierDefinition(
        Long id,
        FareChargeType chargeType,
        long rangeStart,
        Long rangeEnd,
        long unitSize,
        BigDecimal unitPrice,
        RoundingMode roundingMode,
        int sortNo
) {
}
