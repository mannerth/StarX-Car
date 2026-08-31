package org.mannerth.starxcar.order.domain;







/** Values persisted in ride_order.status. */
public enum OrderStatus {
    SCHEDULED,
    PENDING,
    ACCEPTED,
    DRIVER_ARRIVED,
    IN_SERVICE,
    COMPLETED,
    TERMINATED,
    CANCELLED
}
