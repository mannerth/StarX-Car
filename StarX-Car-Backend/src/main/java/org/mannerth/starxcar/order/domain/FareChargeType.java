package org.mannerth.starxcar.order.domain;







/** Charge categories persisted in pricing_rule_tier and order_fare_item. */
public enum FareChargeType {
    BASE,
    ARRIVAL_CANCEL,
    DISTANCE,
    DRIVING_TIME,
    WAITING_TIME
}
