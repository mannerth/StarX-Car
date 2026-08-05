package org.mannerth.starxcar.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PassengerTripLocationServiceImplTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T02:00:30Z"), ZoneOffset.UTC);

    @Mock
    private OrderAccessService orderAccessService;
    @Mock
    private DriverLocationMapper locationMapper;

    private PassengerTripLocationServiceImpl service;

    @BeforeEach
    void setUp() {
        DriverLocationProperties properties = new DriverLocationProperties();
        properties.setFreshnessSeconds(30);
        service = new PassengerTripLocationServiceImpl(orderAccessService, locationMapper, properties, CLOCK);
    }

    @Test
    void returnsFreshOrderBoundDriverLocation() {
        RideOrder order = order(OrderStatus.ACCEPTED);
        DriverLocation location = location(order.getId(), LocalDateTime.of(2026, 8, 5, 10, 0));
        given(orderAccessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(locationMapper.selectById(2L)).willReturn(location);

        DriverLocationResponse response = service.getDriverLocation(1L, "O1");

        assertThat(response.latitude()).isEqualByComparingTo("39.916527");
        assertThat(response.longitude()).isEqualByComparingTo("116.397128");
        assertThat(response.stale()).isFalse();
    }

    @Test
    void returnsLastKnownLocationMarkedStale() {
        RideOrder order = order(OrderStatus.DRIVER_ARRIVED);
        DriverLocation location = location(order.getId(), LocalDateTime.of(2026, 8, 5, 9, 59, 59));
        given(orderAccessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(locationMapper.selectById(2L)).willReturn(location);

        assertThat(service.getDriverLocation(1L, "O1").stale()).isTrue();
    }

    @Test
    void rejectsLocationWhenOrderIsNotInVisibleState() {
        given(orderAccessService.requirePassengerOrder("O1", 1L)).willReturn(order(OrderStatus.COMPLETED));

        assertConflict(() -> service.getDriverLocation(1L, "O1"));
        verify(locationMapper, never()).selectById(2L);
    }

    @Test
    void rejectsLocationBelongingToAnotherOrder() {
        RideOrder order = order(OrderStatus.IN_SERVICE);
        given(orderAccessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(locationMapper.selectById(2L)).willReturn(location(999L, LocalDateTime.of(2026, 8, 5, 10, 0)));

        assertThatThrownBy(() -> service.getDriverLocation(1L, "O1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void routeContextRequiresServiceAndUsesDestinationSnapshot() {
        RideOrder order = order(OrderStatus.IN_SERVICE);
        order.setDestinationPoiId("B000A83D7R");
        order.setDestinationName("北京首都国际机场");
        order.setDestinationAddress("北京市顺义区");
        order.setDestinationLat(new BigDecimal("40.079311"));
        order.setDestinationLng(new BigDecimal("116.603112"));
        given(orderAccessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(locationMapper.selectById(2L)).willReturn(location(order.getId(), LocalDateTime.of(2026, 8, 5, 10, 0)));

        RideRouteContextResponse response = service.getRouteContext(1L, "O1");

        assertThat(response.destinationName()).isEqualTo("北京首都国际机场");
        assertThat(response.recommendedRefreshDistanceM()).isEqualTo(100);
        assertThat(response.recommendedRefreshSeconds()).isEqualTo(60);
        assertThat(response.driverLocation().stale()).isFalse();
    }

    @Test
    void routeContextRejectsNonServiceOrder() {
        given(orderAccessService.requirePassengerOrder(anyString(), org.mockito.ArgumentMatchers.eq(1L)))
                .willReturn(order(OrderStatus.DRIVER_ARRIVED));

        assertConflict(() -> service.getRouteContext(1L, "O1"));
    }

    private RideOrder order(OrderStatus status) {
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O1");
        order.setPassengerId(1L);
        order.setDriverId(2L);
        order.setStatus(status);
        return order;
    }

    private DriverLocation location(Long activeOrderId, LocalDateTime receivedTime) {
        DriverLocation location = new DriverLocation();
        location.setDriverId(2L);
        location.setActiveOrderId(activeOrderId);
        location.setLatitude(new BigDecimal("39.916527"));
        location.setLongitude(new BigDecimal("116.397128"));
        location.setAccuracyM(new BigDecimal("8.50"));
        location.setSpeedMps(new BigDecimal("12.30"));
        location.setBearing(new BigDecimal("180.00"));
        location.setRecordedTime(receivedTime.minusSeconds(3));
        location.setReceivedTime(receivedTime);
        return location;
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
