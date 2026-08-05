package org.mannerth.starxcar.order;

/** Charge categories persisted in pricing_rule_tier and order_fare_item. */
public enum FareChargeType {
    BASE,
    DISTANCE,
    DRIVING_TIME,
    WAITING_TIME
}
