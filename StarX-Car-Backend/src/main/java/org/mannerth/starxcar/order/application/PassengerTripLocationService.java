package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;






/** Provides a passenger's read-only, order-scoped view of the assigned driver's latest location. */
public interface PassengerTripLocationService {
    DriverLocationResponse getDriverLocation(Long passengerId, String orderNo);

    RideRouteContextResponse getRouteContext(Long passengerId, String orderNo);
}