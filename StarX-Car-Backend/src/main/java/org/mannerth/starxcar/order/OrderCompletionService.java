package org.mannerth.starxcar.order;

import org.mannerth.starxcar.order.response.OrderCompletionResponse;

/** Finalizes an in-service order using its pricing-rule snapshot. */
public interface OrderCompletionService {
    OrderCompletionResponse complete(Long driverId, String orderNo);
}
