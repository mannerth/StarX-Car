package org.mannerth.starxcar.order.admin.response;

import java.util.List;

public record AdminOrderPageResponse(long page, long size, long total, List<AdminOrderSummaryResponse> records) {
}
