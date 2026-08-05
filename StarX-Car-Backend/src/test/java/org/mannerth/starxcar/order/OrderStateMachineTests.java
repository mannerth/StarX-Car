package org.mannerth.starxcar.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStateMachineTests {

    @Test
    void allowsOnlyConfirmedImmediateRideTransitions() {
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.ACCEPT, OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.CANCEL, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.ACCEPTED, OrderEventType.ARRIVE, OrderStatus.DRIVER_ARRIVED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.DRIVER_ARRIVED, OrderEventType.START_SERVICE, OrderStatus.IN_SERVICE)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.IN_SERVICE, OrderEventType.COMPLETE, OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void rejectsTransitionsOutsideConfirmedStateMachine() {
        assertThat(OrderStateMachine.targetStatus(OrderStatus.COMPLETED, OrderEventType.CANCEL)).isEmpty();
        assertThat(OrderStateMachine.allows(OrderStatus.DRIVER_ARRIVED, OrderEventType.CANCEL, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.COMPLETE, OrderStatus.COMPLETED)).isFalse();
    }
}
