package org.mannerth.starxcar.order.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;




class OrderStateMachineTests {

    @Test
    void allowsOnlyConfirmedImmediateRideTransitions() {
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.ACCEPT, OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.CANCEL, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.ACCEPTED, OrderEventType.ARRIVE, OrderStatus.DRIVER_ARRIVED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.DRIVER_ARRIVED, OrderEventType.CANCEL, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.DRIVER_ARRIVED, OrderEventType.START_SERVICE, OrderStatus.IN_SERVICE)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.IN_SERVICE, OrderEventType.COMPLETE, OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.IN_SERVICE, OrderEventType.TERMINATE, OrderStatus.TERMINATED)).isTrue();
    }

    @Test
    void allowsOnlyConfirmedReservationTransitions() {
        assertThat(OrderStateMachine.allows(OrderStatus.SCHEDULED, OrderEventType.DISPATCH, OrderStatus.PENDING)).isTrue();
        assertThat(OrderStateMachine.allows(OrderStatus.SCHEDULED, OrderEventType.CANCEL, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.targetStatus(OrderStatus.SCHEDULED, OrderEventType.ACCEPT)).isEmpty();
        assertThat(OrderStateMachine.allows(OrderStatus.SCHEDULED, OrderEventType.COMPLETE, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.DISPATCH, OrderStatus.PENDING)).isFalse();
    }

    @Test
    void rejectsTransitionsOutsideConfirmedStateMachine() {
        assertThat(OrderStateMachine.targetStatus(OrderStatus.COMPLETED, OrderEventType.CANCEL)).isEmpty();
        assertThat(OrderStateMachine.allows(OrderStatus.DRIVER_ARRIVED, OrderEventType.TERMINATE, OrderStatus.TERMINATED)).isFalse();
        assertThat(OrderStateMachine.allows(OrderStatus.PENDING, OrderEventType.COMPLETE, OrderStatus.COMPLETED)).isFalse();
    }
}
