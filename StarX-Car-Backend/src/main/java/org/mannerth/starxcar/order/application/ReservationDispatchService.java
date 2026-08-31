package org.mannerth.starxcar.order.application;







public interface ReservationDispatchService {

    /** Moves due SCHEDULED reservation orders into PENDING and writes DISPATCH events. Returns moved count. */
    int dispatchDueReservations();

    /** Auto-cancels SCHEDULED PENDING orders whose pickup time passed the expiry grace window. Returns cancelled count. */
    int expireStaleReservations();
}