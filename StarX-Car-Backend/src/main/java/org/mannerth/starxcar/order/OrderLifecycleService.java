package org.mannerth.starxcar.order;

import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;

/** Executes the immediate-ride cancellation, arrival, and service-start transitions. */
public interface OrderLifecycleService {
    OrderStatusTransitionResponse cancelByPassenger(Long passengerId, String orderNo, String reason);

    OrderStatusTransitionResponse cancelByDriver(Long driverId, String orderNo, String reason);

    OrderStatusTransitionResponse markDriverArrived(Long driverId, String orderNo);

    OrderStatusTransitionResponse startService(Long driverId, String orderNo);
}
