package org.mannerth.starxcar.order.domain;







/** Audit events persisted in ride_order_event. */
public enum OrderEventType {
    CREATE,
    DISPATCH,
    ACCEPT,
    ARRIVE,
    START_SERVICE,
    COMPLETE,
    TERMINATE,
    CANCEL
}
