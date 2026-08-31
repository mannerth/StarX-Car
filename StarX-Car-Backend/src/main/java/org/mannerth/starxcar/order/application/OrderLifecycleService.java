package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.order.response.OrderTerminationResponse;






/** Executes the immediate-ride cancellation, arrival, and service-start transitions. */
public interface OrderLifecycleService {
    OrderStatusTransitionResponse cancelByPassenger(Long passengerId, String orderNo, String reason);

    OrderStatusTransitionResponse cancelByDriver(Long driverId, String orderNo, String reason);

    OrderStatusTransitionResponse markDriverArrived(Long driverId, String orderNo);

    /**
     * Starts service for a DRIVER_ARRIVED order after verifying the passenger's
     * four-digit phone tail. A null or mismatching tail is rejected with 400.
     */
    OrderStatusTransitionResponse startService(Long driverId, String orderNo, String phoneTail);

    OrderTerminationResponse terminateByPassenger(Long passengerId, String orderNo, String reasonCode, String reasonDetail);

    OrderTerminationResponse terminateByDriver(Long driverId, String orderNo, String reasonCode, String reasonDetail);
}
