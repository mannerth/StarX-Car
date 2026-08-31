package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.config.ReservationProperties;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;




class ReservationDispatchServiceImplTests {
    private RideOrderMapper orderMapper;
    private RideOrderEventMapper eventMapper;
    private ReservationDispatchService service;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RideOrder.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC);
        service = new ReservationDispatchServiceImpl(orderMapper, eventMapper, new ReservationProperties(), clock);
    }

    @Test
    void dispatchesDueReservationIntoPendingWithDispatchEvent() {
        RideOrder due = order(1L, OrderStatus.SCHEDULED, LocalDateTime.of(2026, 8, 4, 20, 15));
        given(orderMapper.selectList(any())).willReturn(List.of(due));
        given(orderMapper.dispatchReservation(eq(1L), eq(0L), any())).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        int dispatched = service.dispatchDueReservations();

        assertThat(dispatched).isEqualTo(1);
        ArgumentCaptor<RideOrderEvent> eventCaptor = ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        RideOrderEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(OrderEventType.DISPATCH);
        assertThat(event.getFromStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(event.getToStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.getOperatorType()).isEqualTo(OrderOperatorType.system);
        assertThat(event.getOperatorId()).isNull();
    }

    @Test
    void skipsReservationWhenDispatchUpdateWinsNoRows() {
        RideOrder due = order(1L, OrderStatus.SCHEDULED, LocalDateTime.of(2026, 8, 4, 20, 15));
        given(orderMapper.selectList(any())).willReturn(List.of(due));
        given(orderMapper.dispatchReservation(eq(1L), eq(0L), any())).willReturn(0);

        int dispatched = service.dispatchDueReservations();

        assertThat(dispatched).isZero();
        verify(eventMapper, never()).insert(any(RideOrderEvent.class));
    }

    @Test
    void expiresStalePendingReservationAfterGraceWindowWithSystemCancel() {
        RideOrder stale = order(2L, OrderStatus.PENDING, LocalDateTime.of(2026, 8, 4, 19, 20));
        ReservationProperties properties = new ReservationProperties();
        properties.setExpireGraceMinutes(45);
        service = new ReservationDispatchServiceImpl(orderMapper, eventMapper, properties,
                Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC));
        given(orderMapper.selectList(any())).willReturn(List.of(stale));
        given(orderMapper.cancelOrder(eq(2L), eq(OrderStatus.PENDING), eq(0L), any(), eq("system"), any())).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        int cancelled = service.expireStaleReservations();

        assertThat(cancelled).isEqualTo(1);
        ArgumentCaptor<RideOrderEvent> eventCaptor = ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(OrderEventType.CANCEL);
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(eventCaptor.getValue().getOperatorType()).isEqualTo(OrderOperatorType.system);
        assertThat(eventCaptor.getValue().getRemark()).contains("45分钟");
    }

    @Test
    void dispatcherQueriesAreScopedToScheduledTypeAndPickupTime() {
        given(orderMapper.selectList(any())).willReturn(List.of());

        service.dispatchDueReservations();
        service.expireStaleReservations();

        ArgumentCaptor<LambdaQueryWrapper<RideOrder>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orderMapper, times(2)).selectList(captor.capture());
        String dispatchSql = captor.getAllValues().get(0).getCustomSqlSegment();
        String expireSql = captor.getAllValues().get(1).getCustomSqlSegment();
        assertThat(dispatchSql).contains("order_type").contains("status").contains("scheduled_pickup_time");
        assertThat(expireSql).contains("order_type").contains("status").contains("scheduled_pickup_time");
    }

    private RideOrder order(Long id, OrderStatus status, LocalDateTime pickupTime) {
        RideOrder order = new RideOrder();
        order.setId(id);
        order.setOrderType(OrderType.SCHEDULED);
        order.setStatus(status);
        order.setScheduledPickupTime(pickupTime);
        order.setVersion(0L);
        return order;
    }
}