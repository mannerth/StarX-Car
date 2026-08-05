package org.mannerth.starxcar.order;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The single source of truth for the immediate-ride order state transitions.
 * Role and ownership checks remain in the workflow service because they require order data.
 */
public final class OrderStateMachine {
    private static final Map<OrderStatus, Map<OrderEventType, OrderStatus>> TRANSITIONS = transitions();

    private OrderStateMachine() {
    }

    public static Optional<OrderStatus> targetStatus(OrderStatus currentStatus, OrderEventType eventType) {
        return Optional.ofNullable(TRANSITIONS.getOrDefault(currentStatus, Map.of()).get(eventType));
    }

    public static boolean allows(OrderStatus currentStatus, OrderEventType eventType, OrderStatus targetStatus) {
        return targetStatus(currentStatus, eventType).filter(targetStatus::equals).isPresent();
    }

    private static Map<OrderStatus, Map<OrderEventType, OrderStatus>> transitions() {
        Map<OrderStatus, Map<OrderEventType, OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.PENDING, transitions(OrderEventType.ACCEPT, OrderStatus.ACCEPTED,
                OrderEventType.CANCEL, OrderStatus.CANCELLED));
        transitions.put(OrderStatus.ACCEPTED, transitions(OrderEventType.ARRIVE, OrderStatus.DRIVER_ARRIVED,
                OrderEventType.CANCEL, OrderStatus.CANCELLED));
        transitions.put(OrderStatus.DRIVER_ARRIVED, transitions(OrderEventType.START_SERVICE, OrderStatus.IN_SERVICE));
        transitions.put(OrderStatus.IN_SERVICE, transitions(OrderEventType.COMPLETE, OrderStatus.COMPLETED));
        return Map.copyOf(transitions);
    }

    private static Map<OrderEventType, OrderStatus> transitions(Object... values) {
        Map<OrderEventType, OrderStatus> transitions = new EnumMap<>(OrderEventType.class);
        for (int index = 0; index < values.length; index += 2) {
            transitions.put((OrderEventType) values[index], (OrderStatus) values[index + 1]);
        }
        return Map.copyOf(transitions);
    }
}
