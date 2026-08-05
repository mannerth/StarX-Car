package org.mannerth.starxcar.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.order.DriverLocation;
import org.mannerth.starxcar.order.DriverLocationMapper;
import org.mannerth.starxcar.order.RideOrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DriverWorkServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-05T02:00:00Z");

    private DriverMapper driverMapper;
    private UserMapper userMapper;
    private CarMapper carMapper;
    private DriverCarBindingMapper bindingMapper;
    private RideOrderMapper rideOrderMapper;
    private DriverLocationMapper locationMapper;
    private DriverWorkService service;

    @BeforeEach
    void setUp() {
        driverMapper = mock(DriverMapper.class);
        userMapper = mock(UserMapper.class);
        carMapper = mock(CarMapper.class);
        bindingMapper = mock(DriverCarBindingMapper.class);
        rideOrderMapper = mock(RideOrderMapper.class);
        locationMapper = mock(DriverLocationMapper.class);
        DriverLocationProperties properties = new DriverLocationProperties();
        properties.setMaxRecordedTimeFutureSeconds(60);
        service = new DriverWorkService(driverMapper, userMapper, carMapper, bindingMapper, rideOrderMapper,
                locationMapper, properties, Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void approvedDriverWithActiveBoundCurrentCarCanGoOnline() {
        Driver driver = activeDriver("offline", 7L);
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(userMapper.selectById(1L)).willReturn(activeUser());
        given(rideOrderMapper.selectCount(any())).willReturn(0L);
        Car car = new Car();
        car.setId(7L);
        car.setVehicleStatus("active");
        given(carMapper.selectById(7L)).willReturn(car);
        given(bindingMapper.exists(any())).willReturn(true);

        Driver updated = service.updateWorkStatus(1L, "online");

        assertThat(updated.getWorkStatus()).isEqualTo("online");
        verify(driverMapper).updateById(driver);
    }

    @Test
    void onlineTransitionRejectsMissingInvalidOrUnboundCurrentCar() {
        Driver driver = activeDriver("offline", null);
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(userMapper.selectById(1L)).willReturn(activeUser());
        given(rideOrderMapper.selectCount(any())).willReturn(0L);

        assertConflict(() -> service.updateWorkStatus(1L, "online"));

        driver.setCarId(7L);
        Car car = new Car();
        car.setVehicleStatus("disabled");
        given(carMapper.selectById(7L)).willReturn(car);
        assertConflict(() -> service.updateWorkStatus(1L, "online"));

        car.setVehicleStatus("active");
        given(bindingMapper.exists(any())).willReturn(false);
        assertConflict(() -> service.updateWorkStatus(1L, "online"));
        verify(driverMapper, never()).updateById(any(Driver.class));
    }

    @Test
    void inactiveOrUnapprovedDriverCannotWorkOrReportLocation() {
        Driver driver = activeDriver("online", 7L);
        driver.setApprovalStatus("pending");
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(userMapper.selectById(1L)).willReturn(activeUser());
        assertForbidden(() -> service.updateWorkStatus(1L, "offline"));

        driver.setApprovalStatus("approved");
        User cancelled = activeUser();
        cancelled.setStatus("cancelled");
        given(userMapper.selectById(1L)).willReturn(cancelled);
        assertForbidden(() -> service.reportLocation(1L, locationAt(now())));
        verify(locationMapper, never()).upsertLatestLocation(any());
    }

    @Test
    void driverWithOngoingOrderCannotGoOffline() {
        Driver driver = activeDriver("busy", 7L);
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(userMapper.selectById(1L)).willReturn(activeUser());
        given(rideOrderMapper.selectCount(any())).willReturn(1L);

        assertConflict(() -> service.updateWorkStatus(1L, "offline"));
        verify(driverMapper, never()).updateById(any(Driver.class));
    }

    @Test
    void offlineDriverCannotReportLocation() {
        given(driverMapper.selectById(1L)).willReturn(activeDriver("offline", 7L));
        given(userMapper.selectById(1L)).willReturn(activeUser());

        assertConflict(() -> service.reportLocation(1L, locationAt(now())));
        verify(locationMapper, never()).upsertLatestLocation(any());
    }

    @Test
    void onlineDriverReportsPositionWithoutControllingActiveOrder() {
        given(driverMapper.selectById(1L)).willReturn(activeDriver("online", 7L));
        given(userMapper.selectById(1L)).willReturn(activeUser());
        given(locationMapper.upsertLatestLocation(any())).willReturn(1);

        service.reportLocation(1L, locationAt(now()));

        org.mockito.ArgumentCaptor<DriverLocation> captor = org.mockito.ArgumentCaptor.forClass(DriverLocation.class);
        verify(locationMapper).upsertLatestLocation(captor.capture());
        DriverLocation saved = captor.getValue();
        assertThat(saved.getDriverId()).isEqualTo(1L);
        assertThat(saved.getActiveOrderId()).isNull();
        assertThat(saved.getLatitude()).isEqualByComparingTo("39.916527");
        assertThat(saved.getLongitude()).isEqualByComparingTo("116.397128");
        assertThat(saved.getRecordedTime()).isEqualTo(now());
        assertThat(saved.getReceivedTime()).isEqualTo(now());
    }

    @Test
    void repeatedPositionUpsertReturningZeroIsSuccessful() {
        given(driverMapper.selectById(1L)).willReturn(activeDriver("busy", 7L));
        given(userMapper.selectById(1L)).willReturn(activeUser());
        given(locationMapper.upsertLatestLocation(any())).willReturn(0);

        service.reportLocation(1L, locationAt(now()));

        verify(locationMapper).upsertLatestLocation(any());
    }

    @Test
    void futureDeviceTimeAndUnexpectedUpsertResultAreRejected() {
        given(driverMapper.selectById(1L)).willReturn(activeDriver("online", 7L));
        given(userMapper.selectById(1L)).willReturn(activeUser());

        assertBadRequest(() -> service.reportLocation(1L, locationAt(now().plusSeconds(61))));
        verify(locationMapper, never()).upsertLatestLocation(any());

        given(locationMapper.upsertLatestLocation(any())).willReturn(3);
        assertServerError(() -> service.reportLocation(1L, locationAt(now())));
    }

    private Driver activeDriver(String workStatus, Long carId) {
        Driver driver = new Driver();
        driver.setId(1L);
        driver.setWorkStatus(workStatus);
        driver.setCarId(carId);
        driver.setApprovalStatus("approved");
        return driver;
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setStatus("active");
        return user;
    }

    private DriverLocationRequest locationAt(LocalDateTime time) {
        return new DriverLocationRequest(new BigDecimal("39.916527"), new BigDecimal("116.397128"),
                new BigDecimal("8.5"), new BigDecimal("12.3"), new BigDecimal("180"), time);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Shanghai"));
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertServerError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
