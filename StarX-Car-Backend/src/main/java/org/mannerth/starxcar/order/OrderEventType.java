package org.mannerth.starxcar.order;

/** Audit events persisted in ride_order_event. */
public enum OrderEventType {
    CREATE,
    ACCEPT,
    ARRIVE,
    START_SERVICE,
    COMPLETE,
    CANCEL
}
