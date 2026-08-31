package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import java.math.BigDecimal;






/** One auditable base or pricing-tier result. */
public record FareLine(
        FareChargeType chargeType,
        Long tierId,
        Long rangeStart,
        Long rangeEnd,
        long actualUsage,
        Long unitSize,
        BigDecimal unitPrice,
        long chargedUnits,
        BigDecimal amount
) {
}