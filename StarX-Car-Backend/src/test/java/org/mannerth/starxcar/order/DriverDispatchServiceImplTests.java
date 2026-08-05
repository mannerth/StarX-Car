package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.response.OrderAcceptedResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

class DriverDispatchServiceImplTests {
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-05T02:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));

    private DriverMapper driverMapper;
    private UserMapper userMapper;
    private CarMapper carMapper;
    private DriverCarBindingMapper bindingMapper;
    private DriverLocationMapper locationMapper;
    private RideOrderMapper orderMapper;
    private RideOrderEventMapper eventMapper;
    private OrderAccessService accessService;
    private DriverDispatchService service;

    @BeforeEach
    void setUp() {
        driverMapper = mock(DriverMapper.class);
        userMapper = mock(UserMapper.class);
        carMapper = mock(CarMapper.class);
        bindingMapper = mock(DriverCarBindingMapper.class);
        locationMapper = mock(DriverLocationMapper.class);
        orderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        accessService = mock(OrderAccessService.class);
        DriverLocationProperties properties = new DriverLocationProperties();
        properties.setFreshnessSeconds(30);
        service = new DriverDispatchServiceImpl(driverMapper, userMapper, carMapper, bindingMapper, locationMapper,
                orderMapper, eventMapper, accessService, properties, Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void eligibleDriverCanListPendingOrders() {
        prepareEligibleDriver(1L);
        Page<RideOrder> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(order(10L, "O1", 0L)));
        given(orderMapper.selectPage(any(), any())).willReturn(page);

        OrderPageResponse result = service.listPendingOrders(1L, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.orderNo()).isEqualTo("O1");
            assertThat(item.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(item.estimatedFare()).isTrue();
        });
    }

    @Test
    void acceptsOrderWithVersionConditionAndRecordsAllRequiredSideEffects() {
        prepareEligibleDriver(1L);
        RideOrder order = order(10L, "O1", 3L);
        given(accessService.requireByOrderNo("O1")).willReturn(order);
        given(orderMapper.selectCount(any())).willReturn(0L);
        given(orderMapper.acceptPendingOrder(10L, 1L, 7L, NOW, 3L)).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markBusyIfOnline(1L)).willReturn(1);
        given(locationMapper.attachActiveOrder(1L, 10L, NOW.minusSeconds(30))).willReturn(1);

        OrderAcceptedResponse result = service.acceptOrder(1L, "O1");

        assertThat(result.orderNo()).isEqualTo("O1");
        assertThat(result.status()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(result.carId()).isEqualTo(7L);
        assertThat(result.acceptedTime()).isEqualTo(NOW);
        org.mockito.ArgumentCaptor<RideOrderEvent> event = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue()).satisfies(value -> {
            assertThat(value.getOrderId()).isEqualTo(10L);
            assertThat(value.getEventType()).isEqualTo(OrderEventType.ACCEPT);
            assertThat(value.getFromStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(value.getToStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(value.getOperatorType()).isEqualTo(OrderOperatorType.driver);
            assertThat(value.getOperatorId()).isEqualTo(1L);
            assertThat(value.getCreatedTime()).isEqualTo(NOW);
        });
        verify(driverMapper).markBusyIfOnline(1L);
        verify(locationMapper).attachActiveOrder(1L, 10L, NOW.minusSeconds(30));
    }

    @Test
    void conditionalUpdateFailureRejectsLostOrderWithoutWritingSideEffects() {
        prepareEligibleDriver(1L);
        RideOrder order = order(10L, "O1", 0L);
        given(accessService.requireByOrderNo("O1")).willReturn(order);
        given(orderMapper.selectCount(any())).willReturn(0L);
        given(orderMapper.acceptPendingOrder(anyLong(), anyLong(), anyLong(), any(), anyLong())).willReturn(0);

        assertConflict(() -> service.acceptOrder(1L, "O1"));

        verify(eventMapper, never()).insert(any(RideOrderEvent.class));
        verify(driverMapper, never()).markBusyIfOnline(anyLong());
        verify(locationMapper, never()).attachActiveOrder(anyLong(), anyLong(), any());
    }

    @Test
    void offlineDriverMissingLocationAndExistingOrderAreRejected() {
        prepareEligibleDriver(1L);
        Driver offline = driver(1L);
        offline.setWorkStatus("offline");
        given(driverMapper.selectById(1L)).willReturn(offline);
        assertConflict(() -> service.listPendingOrders(1L, 1, 20));

        prepareEligibleDriver(1L);
        given(locationMapper.selectById(1L)).willReturn(null);
        assertConflict(() -> service.acceptOrder(1L, "O1"));

        prepareEligibleDriver(1L);
        given(orderMapper.selectCount(any())).willReturn(1L);
        assertConflict(() -> service.acceptOrder(1L, "O1"));
    }

    @Test
    void disabledUnapprovedAndInvalidCurrentCarDriversAreRejected() {
        prepareEligibleDriver(1L);
        User disabled = activeUser(1L);
        disabled.setStatus("disabled");
        given(userMapper.selectById(1L)).willReturn(disabled);
        assertForbidden(() -> service.listPendingOrders(1L, 1, 20));

        prepareEligibleDriver(1L);
        Driver unapproved = driver(1L);
        unapproved.setApprovalStatus("pending");
        given(driverMapper.selectById(1L)).willReturn(unapproved);
        assertForbidden(() -> service.listPendingOrders(1L, 1, 20));

        prepareEligibleDriver(1L);
        Driver noCurrentCar = driver(1L);
        noCurrentCar.setCarId(null);
        given(driverMapper.selectById(1L)).willReturn(noCurrentCar);
        assertConflict(() -> service.listPendingOrders(1L, 1, 20));

        prepareEligibleDriver(1L);
        given(bindingMapper.exists(any())).willReturn(false);
        assertConflict(() -> service.listPendingOrders(1L, 1, 20));
    }

    @Test
    void eventOrDriverStatusFailureStopsTheWorkflowBeforeLocationAssociation() {
        prepareEligibleDriver(1L);
        RideOrder order = order(10L, "O1", 0L);
        given(accessService.requireByOrderNo("O1")).willReturn(order);
        given(orderMapper.selectCount(any())).willReturn(0L);
        given(orderMapper.acceptPendingOrder(anyLong(), anyLong(), anyLong(), any(), anyLong())).willReturn(1);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(0);

        assertServerError(() -> service.acceptOrder(1L, "O1"));
        verify(driverMapper, never()).markBusyIfOnline(anyLong());
        verify(locationMapper, never()).attachActiveOrder(anyLong(), anyLong(), any());

        prepareEligibleDriver(1L);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markBusyIfOnline(1L)).willReturn(0);
        assertConflict(() -> service.acceptOrder(1L, "O1"));
        verify(locationMapper, never()).attachActiveOrder(anyLong(), anyLong(), any());
    }

    @Test
    void twoDriversCompetingForOneOrderHaveExactlyOneWinner() throws Exception {
        AtomicBoolean accepted = new AtomicBoolean();
        given(driverMapper.selectById(anyLong())).willAnswer(invocation -> driver(invocation.getArgument(0, Long.class)));
        given(userMapper.selectById(anyLong())).willAnswer(invocation -> activeUser(invocation.getArgument(0, Long.class)));
        given(carMapper.selectById(anyLong())).willAnswer(invocation -> activeCar(invocation.getArgument(0, Long.class)));
        given(bindingMapper.exists(any())).willReturn(true);
        given(locationMapper.selectById(anyLong())).willAnswer(invocation -> freshLocation(invocation.getArgument(0, Long.class)));
        given(orderMapper.selectCount(any())).willReturn(0L);
        RideOrder sharedOrder = order(10L, "O1", 0L);
        given(accessService.requireByOrderNo("O1")).willReturn(sharedOrder);
        given(orderMapper.acceptPendingOrder(anyLong(), anyLong(), anyLong(), any(), anyLong()))
                .willAnswer(invocation -> accepted.compareAndSet(false, true) ? 1 : 0);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markBusyIfOnline(anyLong())).willReturn(1);
        given(locationMapper.attachActiveOrder(anyLong(), anyLong(), any())).willReturn(1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> acceptWhenStarted(1L, ready, start));
            Future<Boolean> second = executor.submit(() -> acceptWhenStarted(2L, ready, start));
            assertThat(ready.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        verify(eventMapper).insert(any(RideOrderEvent.class));
    }

    @Test
    void acceptanceUsesTheRequiredAtomicSqlConditionInsideATransaction() throws Exception {
        Update update = RideOrderMapper.class.getMethod("acceptPendingOrder", Long.class, Long.class, Long.class,
                LocalDateTime.class, Long.class).getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertThat(sql).contains("status = 'PENDING'", "driver_id IS NULL", "version = #{version}",
                "version = version + 1");
        assertThat(DriverDispatchServiceImpl.class.getMethod("acceptOrder", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private boolean acceptWhenStarted(Long driverId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.acceptOrder(driverId, "O1");
            return true;
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return false;
        }
    }

    private void prepareEligibleDriver(Long driverId) {
        given(driverMapper.selectById(driverId)).willReturn(driver(driverId));
        given(userMapper.selectById(driverId)).willReturn(activeUser(driverId));
        given(carMapper.selectById(7L)).willReturn(activeCar(7L));
        given(bindingMapper.exists(any())).willReturn(true);
        given(locationMapper.selectById(driverId)).willReturn(freshLocation(driverId));
    }

    private Driver driver(Long id) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setApprovalStatus("approved");
        driver.setWorkStatus("online");
        driver.setCarId(7L);
        return driver;
    }

    private User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setStatus("active");
        return user;
    }

    private Car activeCar(Long id) {
        Car car = new Car();
        car.setId(id);
        car.setVehicleStatus("active");
        return car;
    }

    private DriverLocation freshLocation(Long driverId) {
        DriverLocation location = new DriverLocation();
        location.setDriverId(driverId);
        location.setReceivedTime(NOW);
        return location;
    }

    private RideOrder order(Long id, String orderNo, Long version) {
        RideOrder order = new RideOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(OrderStatus.PENDING);
        order.setVersion(version);
        order.setPickupName("天安门");
        order.setDestinationName("外滩");
        order.setTotalFare(new BigDecimal("42.70"));
        order.setCreatedTime(NOW.minusMinutes(1));
        return order;
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void assertServerError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
