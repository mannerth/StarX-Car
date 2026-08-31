package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.car.domain.Car;
import org.mannerth.starxcar.car.infrastructure.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.domain.Driver;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.response.FareQuoteResponse;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderDriverResponse;
import org.mannerth.starxcar.order.response.OrderEventResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.order.response.OrderSummaryResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;






@Service
public class OrderQueryServiceImpl implements OrderQueryService {
    private final RideOrderMapper rideOrderMapper;
    private final RideOrderEventMapper rideOrderEventMapper;
    private final OrderAccessService orderAccessService;
    private final DriverMapper driverMapper;
    private final CarMapper carMapper;
    private final PassengerMapper passengerMapper;

    public OrderQueryServiceImpl(RideOrderMapper rideOrderMapper, RideOrderEventMapper rideOrderEventMapper,
                                 OrderAccessService orderAccessService, DriverMapper driverMapper, CarMapper carMapper,
                                 PassengerMapper passengerMapper) {
        this.rideOrderMapper = rideOrderMapper;
        this.rideOrderEventMapper = rideOrderEventMapper;
        this.orderAccessService = orderAccessService;
        this.driverMapper = driverMapper;
        this.carMapper = carMapper;
        this.passengerMapper = passengerMapper;
    }

    @Override
    public OrderPageResponse listPassengerOrders(Long passengerId, long page, long size, OrderStatus status) {
        return page(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getPassengerId, passengerId)
                .eq(status != null, RideOrder::getStatus, status)
                .orderByDesc(RideOrder::getCreatedTime)
                .orderByDesc(RideOrder::getId), page, size);
    }

    @Override
    public OrderPageResponse listDriverOrders(Long driverId, long page, long size, OrderStatus status) {
        return page(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getDriverId, driverId)
                .eq(status != null, RideOrder::getStatus, status)
                .orderByDesc(RideOrder::getCreatedTime)
                .orderByDesc(RideOrder::getId), page, size);
    }

    @Override
    public OrderDetailResponse getOrder(Long requesterId, UserType requesterType, String orderNo) {
        RideOrder order = switch (requesterType) {
            case passenger -> orderAccessService.requirePassengerOrder(orderNo, requesterId);
            case driver -> orderAccessService.requireDriverOrder(orderNo, requesterId);
            case admin -> orderAccessService.requireByOrderNo(orderNo);
        };
        List<OrderEventResponse> events = rideOrderEventMapper.selectList(new LambdaQueryWrapper<RideOrderEvent>()
                        .eq(RideOrderEvent::getOrderId, order.getId())
                        .orderByAsc(RideOrderEvent::getCreatedTime)
                        .orderByAsc(RideOrderEvent::getId))
                .stream().map(event -> new OrderEventResponse(event.getEventType(), event.getFromStatus(),
                        event.getToStatus(), event.getOperatorType(), event.getCreatedTime(), event.getRemark())).toList();
        return new OrderDetailResponse(order.getOrderNo(), order.getStatus(), order.getOrderType(),
                order.getScheduledPickupTime(), order.getDispatchTime(), order.getPickupPoiId(), order.getPickupName(),
                order.getPickupAddress(), order.getPickupLat(), order.getPickupLng(), order.getDestinationPoiId(),
                order.getDestinationName(), order.getDestinationAddress(), order.getDestinationLat(), order.getDestinationLng(),
                order.getEstimatedDistanceM(), order.getEstimatedDurationS(), order.getBillingDistanceM(),
                order.getActualDurationS(), order.getWaitingDurationS(), toFare(order), !isFinalFare(order),
                passengerPhoneTail(order), driver(order), order.getCreatedTime(), order.getAcceptedTime(),
                order.getDriverArrivedTime(), order.getServiceStartedTime(),
                order.getCompletedTime(), order.getCancelledTime(), order.getCancelledBy(), order.getCancelReason(), events);
    }

    private OrderPageResponse page(LambdaQueryWrapper<RideOrder> query, long page, long size) {
        IPage<RideOrder> result = rideOrderMapper.selectPage(new Page<>(page, size), query);
        List<OrderSummaryResponse> records = result.getRecords().stream().map(order -> new OrderSummaryResponse(
                order.getOrderNo(), order.getStatus(), order.getOrderType(), order.getScheduledPickupTime(),
                order.getPickupName(), order.getDestinationName(),
                order.getTotalFare(), order.getCreatedTime(), !isFinalFare(order))).toList();
        return new OrderPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private String passengerPhoneTail(RideOrder order) {
        if (order.getPassengerId() == null) {
            return null;
        }
        Passenger passenger = passengerMapper.selectById(order.getPassengerId());
        return passenger == null ? null : passenger.getPhoneTail();
    }

    private OrderDriverResponse driver(RideOrder order) {
        if (order.getDriverId() == null) {
            return null;
        }
        Driver driver = driverMapper.selectById(order.getDriverId());
        Car car = order.getCarId() == null ? null : carMapper.selectById(order.getCarId());
        return new OrderDriverResponse(order.getDriverId(),
                driver == null ? null : driver.getPhoneNumber(),
                driver == null ? null : driver.getDisplayPhone(),
                driver == null ? null : driver.getRating(),
                car == null ? null : car.getCarNumber(),
                car == null ? null : car.getBrand(),
                car == null ? null : car.getColor(),
                car == null ? null : car.getCarType());
    }

    private FareQuoteResponse toFare(RideOrder order) {
        return new FareQuoteResponse(order.getBaseFare(), order.getDistanceFare(), order.getDrivingTimeFare(),
                order.getWaitingFare(), order.getTotalFare());
    }

    private boolean isFinalFare(RideOrder order) {
        return order.getStatus() == OrderStatus.COMPLETED;
    }
}