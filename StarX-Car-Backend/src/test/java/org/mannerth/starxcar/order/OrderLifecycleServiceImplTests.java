package org.mannerth.starxcar.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Update;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderLifecycleServiceImplTests {
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-05T02:10:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));

    private RideOrderMapper orderMapper;
    private RideOrderEventMapper eventMapper;
    private DriverMapper driverMapper;
    private DriverLocationMapper locationMapper;
    private OrderAccessService accessService;
    private OrderLifecycleService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        driverMapper = mock(DriverMapper.class);
        locationMapper = mock(DriverLocationMapper.class);
        accessService = mock(OrderAccessService.class);
        service = new OrderLifecycleServiceImpl(orderMapper, eventMapper, driverMapper, locationMapper, accessService,
                Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void passengerCanCancelPendingOrderWithOnePassengerAuditEvent() {
        RideOrder order = order(OrderStatus.PENDING, null, 1L);
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(orderMapper.cancelOrder(10L, OrderStatus.PENDING, 1L, NOW, "passenger", "行程变更")).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderStatusTransitionResponse result = service.cancelByPassenger(1L, "O1", "  行程变更  ");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.operationTime()).isEqualTo(NOW);
        org.mockito.ArgumentCaptor<RideOrderEvent> event = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue()).satisfies(value -> {
            assertThat(value.getEventType()).isEqualTo(OrderEventType.CANCEL);
            assertThat(value.getFromStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(value.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(value.getOperatorType()).isEqualTo(OrderOperatorType.passenger);
            assertThat(value.getOperatorId()).isEqualTo(1L);
            assertThat(value.getRemark()).isEqualTo("行程变更");
        });
        verify(driverMapper, never()).markOnlineIfBusy(anyLong());
        verify(locationMapper, never()).clearActiveOrder(anyLong(), anyLong());
    }

    @Test
    void cancellingAcceptedOrderReleasesDriverAndOnlyMatchingLocationAssociation() {
        RideOrder order = order(OrderStatus.ACCEPTED, 2L, 7L);
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(orderMapper.cancelOrder(10L, OrderStatus.ACCEPTED, 7L, NOW, "passenger", null)).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markOnlineIfBusy(2L)).willReturn(1);

        service.cancelByPassenger(1L, "O1", "   ");

        verify(driverMapper).markOnlineIfBusy(2L);
        verify(locationMapper).clearActiveOrder(2L, 10L);
    }

    @Test
    void driverCanOnlyCancelOwnAcceptedOrder() {
        RideOrder order = order(OrderStatus.ACCEPTED, 2L, 7L);
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(orderMapper.cancelOrder(10L, OrderStatus.ACCEPTED, 7L, NOW, "driver", null)).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markOnlineIfBusy(2L)).willReturn(1);

        service.cancelByDriver(2L, "O1", null);

        org.mockito.ArgumentCaptor<RideOrderEvent> event = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue().getOperatorType()).isEqualTo(OrderOperatorType.driver);

        order.setStatus(OrderStatus.DRIVER_ARRIVED);
        assertConflict(() -> service.cancelByDriver(2L, "O1", null));
    }

    @Test
    void driverArrivalUsesVersionConditionAndWritesArrivalEvent() {
        RideOrder order = order(OrderStatus.ACCEPTED, 2L, 7L);
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(orderMapper.markDriverArrived(10L, 2L, 7L, NOW)).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderStatusTransitionResponse result = service.markDriverArrived(2L, "O1");

        assertThat(result.status()).isEqualTo(OrderStatus.DRIVER_ARRIVED);
        assertThat(result.operationTime()).isEqualTo(NOW);
        org.mockito.ArgumentCaptor<RideOrderEvent> event = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(OrderEventType.ARRIVE);
        assertThat(event.getValue().getOperatorType()).isEqualTo(OrderOperatorType.driver);
    }

    @Test
    void startServiceCalculatesWaitingSecondsFromServerClock() {
        RideOrder order = order(OrderStatus.DRIVER_ARRIVED, 2L, 7L);
        order.setDriverArrivedTime(NOW.minusSeconds(301));
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(orderMapper.startService(10L, 2L, 7L, NOW, 301L)).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderStatusTransitionResponse result = service.startService(2L, "O1");

        assertThat(result.status()).isEqualTo(OrderStatus.IN_SERVICE);
        assertThat(result.waitingDurationS()).isEqualTo(301L);
        org.mockito.ArgumentCaptor<RideOrderEvent> event = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(OrderEventType.START_SERVICE);
    }

    @Test
    void staleOrInvalidTransitionConditionDoesNotWriteEvent() {
        RideOrder order = order(OrderStatus.ACCEPTED, 2L, 7L);
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(orderMapper.markDriverArrived(10L, 2L, 7L, NOW)).willReturn(0);

        assertConflict(() -> service.markDriverArrived(2L, "O1"));
        verify(eventMapper, never()).insert(any(RideOrderEvent.class));

        order.setStatus(OrderStatus.DRIVER_ARRIVED);
        order.setDriverArrivedTime(NOW.plusSeconds(1));
        assertConflict(() -> service.startService(2L, "O1"));
        verify(orderMapper, never()).startService(anyLong(), anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void lifecycleMethodsAreTransactional() throws Exception {
        assertThat(OrderLifecycleServiceImpl.class.getMethod("cancelByPassenger", Long.class, String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(OrderLifecycleServiceImpl.class.getMethod("cancelByDriver", Long.class, String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(OrderLifecycleServiceImpl.class.getMethod("markDriverArrived", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(OrderLifecycleServiceImpl.class.getMethod("startService", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void concurrentArrivalRequestsHaveExactlyOneWinnerAndOneEvent() throws Exception {
        RideOrder sharedOrder = order(OrderStatus.ACCEPTED, 2L, 7L);
        AtomicBoolean arrived = new AtomicBoolean();
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(sharedOrder);
        given(orderMapper.markDriverArrived(anyLong(), anyLong(), anyLong(), any()))
                .willAnswer(invocation -> arrived.compareAndSet(false, true) ? 1 : 0);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> arriveWhenStarted(ready, start));
            Future<Boolean> second = executor.submit(() -> arriveWhenStarted(ready, start));
            assertThat(ready.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        verify(eventMapper).insert(any(RideOrderEvent.class));
    }

    @Test
    void lifecycleSqlUsesExpectedStateAndVersionConditions() throws Exception {
        assertSqlContains("cancelOrder", new Class<?>[]{Long.class, OrderStatus.class, Long.class,
                LocalDateTime.class, String.class, String.class}, "status = #{expectedStatus}", "version = #{version}");
        assertSqlContains("markDriverArrived", new Class<?>[]{Long.class, Long.class, Long.class, LocalDateTime.class},
                "status = 'ACCEPTED'", "driver_id = #{driverId}", "version = #{version}");
        assertSqlContains("startService", new Class<?>[]{Long.class, Long.class, Long.class, LocalDateTime.class,
                Long.class}, "status = 'DRIVER_ARRIVED'", "driver_id = #{driverId}", "version = #{version}");
    }

    private boolean arriveWhenStarted(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.markDriverArrived(2L, "O1");
            return true;
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return false;
        }
    }

    private void assertSqlContains(String methodName, Class<?>[] parameterTypes, String... snippets) throws Exception {
        Update update = RideOrderMapper.class.getMethod(methodName, parameterTypes).getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");
        assertThat(sql).contains(snippets);
    }

    private RideOrder order(OrderStatus status, Long driverId, Long version) {
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O1");
        order.setPassengerId(1L);
        order.setDriverId(driverId);
        order.setStatus(status);
        order.setVersion(version);
        return order;
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
