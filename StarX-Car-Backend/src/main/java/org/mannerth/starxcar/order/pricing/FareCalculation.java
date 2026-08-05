package org.mannerth.starxcar.order.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Finalized calculation values. minimumFareAdjustment is included in baseFare and totalFare. */
public record FareCalculation(
        BigDecimal baseFare,
        BigDecimal minimumFareAdjustment,
        BigDecimal distanceFare,
        BigDecimal drivingTimeFare,
        BigDecimal waitingFare,
        BigDecimal totalFare,
        List<FareLine> lines
) {
    public FareCalculation {
        lines = List.copyOf(lines);
    }
}
