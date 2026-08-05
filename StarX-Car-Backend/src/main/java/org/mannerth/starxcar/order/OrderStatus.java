package org.mannerth.starxcar.order;

/** Values persisted in ride_order.status. */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    DRIVER_ARRIVED,
    IN_SERVICE,
    COMPLETED,
    CANCELLED
}
