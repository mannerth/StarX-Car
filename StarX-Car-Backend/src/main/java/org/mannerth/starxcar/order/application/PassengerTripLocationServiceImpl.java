package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.order.domain.DriverLocation;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.infrastructure.DriverLocationMapper;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;






@Service
public class PassengerTripLocationServiceImpl implements PassengerTripLocationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final EnumSet<OrderStatus> LOCATION_VISIBLE_STATUSES = EnumSet.of(
            OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE);
    private static final int RECOMMENDED_REFRESH_DISTANCE_M = 100;
    private static final int RECOMMENDED_REFRESH_SECONDS = 60;

    private final OrderAccessService orderAccessService;
    private final DriverLocationMapper driverLocationMapper;
    private final DriverLocationProperties locationProperties;
    private final Clock clock;

    public PassengerTripLocationServiceImpl(OrderAccessService orderAccessService,
                                            DriverLocationMapper driverLocationMapper,
                                            DriverLocationProperties locationProperties, Clock clock) {
        this.orderAccessService = orderAccessService;
        this.driverLocationMapper = driverLocationMapper;
        this.locationProperties = locationProperties;
        this.clock = clock;
    }

    @Override
    public DriverLocationResponse getDriverLocation(Long passengerId, String orderNo) {
        RideOrder order = orderAccessService.requirePassengerOrder(orderNo, passengerId);
        if (!LOCATION_VISIBLE_STATUSES.contains(order.getStatus()) || order.getDriverId() == null) {
            throw conflict("订单当前不可查看司机位置");
        }
        return locationFor(order);
    }

    @Override
    public RideRouteContextResponse getRouteContext(Long passengerId, String orderNo) {
        RideOrder order = orderAccessService.requirePassengerOrder(orderNo, passengerId);
        if (order.getStatus() != OrderStatus.IN_SERVICE || order.getDriverId() == null) {
            throw conflict("订单当前不可获取路线规划上下文");
        }
        return new RideRouteContextResponse(locationFor(order), order.getDestinationPoiId(), order.getDestinationName(),
                order.getDestinationAddress(), order.getDestinationLat(), order.getDestinationLng(),
                RECOMMENDED_REFRESH_DISTANCE_M, RECOMMENDED_REFRESH_SECONDS);
    }

    private DriverLocationResponse locationFor(RideOrder order) {
        DriverLocation location = driverLocationMapper.selectById(order.getDriverId());
        if (location == null || !order.getId().equals(location.getActiveOrderId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单暂无司机位置");
        }
        boolean stale = location.getReceivedTime() == null
                || location.getReceivedTime().isBefore(now().minusSeconds(locationProperties.getFreshnessSeconds()));
        return new DriverLocationResponse(location.getLatitude(), location.getLongitude(), location.getAccuracyM(),
                location.getSpeedMps(), location.getBearing(), location.getRecordedTime(), location.getReceivedTime(), stale);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}