package org.mannerth.starxcar.order.admin.application;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.order.admin.domain.AdminOperationLog;
import org.mannerth.starxcar.order.admin.domain.OrderOperationNote;
import org.mannerth.starxcar.order.admin.infrastructure.AdminOperationLogMapper;
import org.mannerth.starxcar.order.admin.infrastructure.OrderOperationNoteMapper;
import org.mannerth.starxcar.order.application.OrderAccessService;
import org.mannerth.starxcar.order.application.OrderQueryService;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.DriverLocationMapper;
import org.mannerth.starxcar.order.infrastructure.OrderFareItemMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;




class AdminOrderServiceImplTests {
    private RideOrderMapper orderMapper;
    private RideOrderEventMapper eventMapper;
    private OrderOperationNoteMapper noteMapper;
    private AdminOperationLogMapper logMapper;
    private OrderAccessService accessService;
    private UserMapper userMapper;
    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        noteMapper = mock(OrderOperationNoteMapper.class);
        logMapper = mock(AdminOperationLogMapper.class);
        accessService = mock(OrderAccessService.class);
        userMapper = mock(UserMapper.class);
        service = new AdminOrderServiceImpl(orderMapper, eventMapper, mock(OrderFareItemMapper.class),
                mock(DriverLocationMapper.class), noteMapper, logMapper, accessService, mock(OrderQueryService.class),
                userMapper, mock(PassengerMapper.class), mock(DriverMapper.class),
                Clock.fixed(Instant.parse("2026-08-31T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void dispatchesOnlyScheduledReservationAndAuditsTheTransition() {
        RideOrder order = order(OrderType.SCHEDULED, OrderStatus.SCHEDULED);
        given(accessService.requireByOrderNo("O1")).willReturn(order);
        given(orderMapper.dispatchReservation(eq(1L), eq(0L), any())).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(logMapper.insert(any(AdminOperationLog.class))).willReturn(1);

        OrderStatusTransitionResponse result = service.dispatchReservation(9L, "O1", "客服确认提前安排");

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        verify(eventMapper).insert(org.mockito.ArgumentMatchers.<RideOrderEvent>argThat(event -> event.getEventType() == OrderEventType.DISPATCH
                && event.getOperatorType() == OrderOperatorType.admin && event.getFromStatus() == OrderStatus.SCHEDULED));
        verify(logMapper).insert(org.mockito.ArgumentMatchers.<AdminOperationLog>argThat(log -> "RESERVATION_DISPATCH".equals(log.getAction())
                && log.getBeforeStatus() == OrderStatus.SCHEDULED && log.getAfterStatus() == OrderStatus.PENDING));
    }

    @Test
    void cancelsOnlyPendingOrScheduledOrderAndAuditsTheTransition() {
        RideOrder order = order(OrderType.INSTANT, OrderStatus.PENDING);
        given(accessService.requireByOrderNo("O2")).willReturn(order);
        given(orderMapper.cancelOrder(eq(1L), eq(OrderStatus.PENDING), eq(0L), any(), eq("admin"), any())).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(logMapper.insert(any(AdminOperationLog.class))).willReturn(1);

        OrderStatusTransitionResponse result = service.cancel(9L, "O2", "重复订单确认取消");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventMapper).insert(org.mockito.ArgumentMatchers.<RideOrderEvent>argThat(event -> event.getEventType() == OrderEventType.CANCEL
                && event.getOperatorType() == OrderOperatorType.admin));
        verify(logMapper).insert(org.mockito.ArgumentMatchers.<AdminOperationLog>argThat(log -> "ORDER_CANCEL".equals(log.getAction())
                && log.getAfterStatus() == OrderStatus.CANCELLED));
    }

    @Test
    void appendsOperationNoteAndWritesSeparateAuditLog() {
        RideOrder order = order(OrderType.SCHEDULED, OrderStatus.SCHEDULED);
        given(accessService.requireByOrderNo("O3")).willReturn(order);
        given(noteMapper.insert(any(OrderOperationNote.class))).willAnswer(invocation -> {
            invocation.getArgument(0, OrderOperationNote.class).setId(3L);
            return 1;
        });
        given(logMapper.insert(any(AdminOperationLog.class))).willReturn(1);
        User admin = new User(); admin.setId(9L); admin.setUsername("operator");
        given(userMapper.selectById(9L)).willReturn(admin);

        var result = service.addNote(9L, "O3", "已通知司机注意预约时间");

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.adminName()).isEqualTo("operator");
        verify(logMapper).insert(org.mockito.ArgumentMatchers.<AdminOperationLog>argThat(log -> "ORDER_NOTE_CREATE".equals(log.getAction())
                && log.getBeforeStatus() == OrderStatus.SCHEDULED && log.getAfterStatus() == OrderStatus.SCHEDULED));
    }

    private RideOrder order(OrderType type, OrderStatus status) {
        RideOrder order = new RideOrder();
        order.setId(1L); order.setOrderNo("O1"); order.setOrderType(type); order.setStatus(status); order.setVersion(0L);
        return order;
    }
}