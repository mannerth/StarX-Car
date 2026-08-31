package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStateMachine;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.DriverLocationMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;






@Service
public class OrderLifecycleServiceImpl implements OrderLifecycleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RideOrderMapper orderMapper;
    private final RideOrderEventMapper eventMapper;
    private final DriverMapper driverMapper;
    private final DriverLocationMapper locationMapper;
    private final PassengerMapper passengerMapper;
    private final OrderAccessService orderAccessService;
    private final Clock clock;

    public OrderLifecycleServiceImpl(RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                     DriverMapper driverMapper, DriverLocationMapper locationMapper,
                                     PassengerMapper passengerMapper, OrderAccessService orderAccessService,
                                     Clock clock) {
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.driverMapper = driverMapper;
        this.locationMapper = locationMapper;
        this.passengerMapper = passengerMapper;
        this.orderAccessService = orderAccessService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse cancelByPassenger(Long passengerId, String orderNo, String reason) {
        RideOrder order = orderAccessService.requirePassengerOrder(orderNo, passengerId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.ACCEPTED
                && order.getStatus() != OrderStatus.SCHEDULED) {
            throw conflict("订单当前不可取消");
        }
        return cancel(order, OrderOperatorType.passenger, passengerId, reason);
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse cancelByDriver(Long driverId, String orderNo, String reason) {
        RideOrder order = orderAccessService.requireDriverOrder(orderNo, driverId);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw conflict("订单当前不可取消");
        }
        return cancel(order, OrderOperatorType.driver, driverId, reason);
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse markDriverArrived(Long driverId, String orderNo) {
        RideOrder order = orderAccessService.requireDriverOrder(orderNo, driverId);
        requireTransition(order, OrderEventType.ARRIVE, OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED);
        LocalDateTime arrivedTime = now();
        if (orderMapper.markDriverArrived(order.getId(), driverId, order.getVersion(), arrivedTime) != 1) {
            throw conflict("订单状态已变化");
        }
        writeEvent(order.getId(), OrderEventType.ARRIVE, OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED,
                OrderOperatorType.driver, driverId, arrivedTime, null);
        return new OrderStatusTransitionResponse(order.getOrderNo(), OrderStatus.DRIVER_ARRIVED, arrivedTime, null);
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse startService(Long driverId, String orderNo, String phoneTail) {
        RideOrder order = orderAccessService.requireDriverOrder(orderNo, driverId);
        requireTransition(order, OrderEventType.START_SERVICE, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE);
        if (phoneTail == null || !phoneTail.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "乘客手机尾号必须为 4 位数字");
        }
        Passenger passenger = passengerMapper.selectById(order.getPassengerId());
        if (passenger == null || !phoneTail.equals(passenger.getPhoneTail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "乘客手机尾号校验失败");
        }
        if (order.getDriverArrivedTime() == null) {
            throw conflict("订单缺少司机到达时间");
        }
        LocalDateTime startedTime = now();
        long waitingDurationS = Duration.between(order.getDriverArrivedTime(), startedTime).getSeconds();
        if (waitingDurationS < 0) {
            throw conflict("订单到达时间异常");
        }
        if (orderMapper.startService(order.getId(), driverId, order.getVersion(), startedTime, waitingDurationS) != 1) {
            throw conflict("订单状态已变化");
        }
        writeEvent(order.getId(), OrderEventType.START_SERVICE, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE,
                OrderOperatorType.driver, driverId, startedTime, null);
        return new OrderStatusTransitionResponse(order.getOrderNo(), OrderStatus.IN_SERVICE, startedTime,
                waitingDurationS);
    }

    private OrderStatusTransitionResponse cancel(RideOrder order, OrderOperatorType operatorType, Long operatorId,
                                                 String reason) {
        requireTransition(order, OrderEventType.CANCEL, order.getStatus(), OrderStatus.CANCELLED);
        LocalDateTime cancelledTime = now();
        String normalizedReason = normalizeReason(reason);
        if (orderMapper.cancelOrder(order.getId(), order.getStatus(), order.getVersion(), cancelledTime,
                operatorType.name(), normalizedReason) != 1) {
            throw conflict("订单状态已变化");
        }
        writeEvent(order.getId(), OrderEventType.CANCEL, order.getStatus(), OrderStatus.CANCELLED, operatorType,
                operatorId, cancelledTime, normalizedReason);
        if (order.getDriverId() != null) {
            if (driverMapper.markOnlineIfBusy(order.getDriverId()) != 1) {
                throw conflict("司机出车状态已变化");
            }
            locationMapper.clearActiveOrder(order.getDriverId(), order.getId());
        }
        return new OrderStatusTransitionResponse(order.getOrderNo(), OrderStatus.CANCELLED, cancelledTime, null);
    }

    private void requireTransition(RideOrder order, OrderEventType eventType, OrderStatus expectedStatus,
                                   OrderStatus targetStatus) {
        if (order.getStatus() != expectedStatus || !OrderStateMachine.allows(order.getStatus(), eventType, targetStatus)) {
            throw conflict("订单当前状态不允许该操作");
        }
    }

    private void writeEvent(Long orderId, OrderEventType eventType, OrderStatus fromStatus, OrderStatus toStatus,
                            OrderOperatorType operatorType, Long operatorId, LocalDateTime time, String remark) {
        RideOrderEvent event = new RideOrderEvent();
        event.setOrderId(orderId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorType(operatorType);
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        event.setCreatedTime(time);
        if (eventMapper.insert(event) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录订单事件失败");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}