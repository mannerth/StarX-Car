package org.mannerth.starxcar.order;

import org.mannerth.starxcar.order.response.OrderAcceptedResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;

/** Driver-facing pending-order discovery and atomic order acceptance workflow. */
public interface DriverDispatchService {
    OrderPageResponse listPendingOrders(Long driverId, long page, long size);

    OrderAcceptedResponse acceptOrder(Long driverId, String orderNo);
}
