package org.mannerth.starxcar.order.admin.response;

import java.math.BigDecimal;






public record AdminOrderSummaryMetricsResponse(
        long total, long instant, long scheduled, long scheduledWaitingDispatch, long pending,
        long accepted, long inService, long completed, long cancelled, long reservationAtRisk,
        BigDecimal completedFare
) {
}