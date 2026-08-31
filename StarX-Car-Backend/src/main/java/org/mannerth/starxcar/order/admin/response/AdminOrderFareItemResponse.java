package org.mannerth.starxcar.order.admin.response;

import org.mannerth.starxcar.order.domain.FareChargeType;
import java.math.BigDecimal;






public record AdminOrderFareItemResponse(FareChargeType chargeType, Integer rangeStart, Integer rangeEnd,
                                         Long actualUsage, Integer unitSize, BigDecimal unitPrice,
                                         Long chargedUnits, BigDecimal amount) {
}