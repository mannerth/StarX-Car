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
import org.mannerth.starxcar.order.infrastructure.OrderCancellationMapper;
import org.mannerth.starxcar.order.infrastructure.OrderFareItemMapper;
import org.mannerth.starxcar.order.domain.OrderCancellation;
import org.mannerth.starxcar.order.domain.OrderFareItem;
import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.DriverLocation;
import org.mannerth.starxcar.order.payment.application.OrderSettlementService;
import org.mannerth.starxcar.order.pricing.application.PricingRuleService;
import org.mannerth.starxcar.order.pricing.domain.FareCalculation;
import org.mannerth.starxcar.order.pricing.domain.FareCalculator;
import org.mannerth.starxcar.order.pricing.domain.FareLine;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleDefinition;
import org.mannerth.starxcar.infrastructure.amap.AmapRouteClient;
import org.mannerth.starxcar.infrastructure.amap.Gcj02Coordinate;
import org.mannerth.starxcar.infrastructure.amap.RouteEstimate;
import org.mannerth.starxcar.order.response.OrderTerminationResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PricingRuleService pricingRuleService;
    private final FareCalculator fareCalculator;
    private final AmapRouteClient amapRouteClient;
    private final OrderFareItemMapper fareItemMapper;
    private final OrderCancellationMapper cancellationMapper;
    private final OrderSettlementService settlementService;

    @Autowired
    public OrderLifecycleServiceImpl(RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                     DriverMapper driverMapper, DriverLocationMapper locationMapper,
                                     PassengerMapper passengerMapper, OrderAccessService orderAccessService,
                                     Clock clock, PricingRuleService pricingRuleService, FareCalculator fareCalculator,
                                     AmapRouteClient amapRouteClient, OrderFareItemMapper fareItemMapper,
                                     OrderCancellationMapper cancellationMapper, OrderSettlementService settlementService) {
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.driverMapper = driverMapper;
        this.locationMapper = locationMapper;
        this.passengerMapper = passengerMapper;
        this.orderAccessService = orderAccessService;
        this.clock = clock;
        this.pricingRuleService = pricingRuleService;
        this.fareCalculator = fareCalculator;
        this.amapRouteClient = amapRouteClient;
        this.fareItemMapper = fareItemMapper;
        this.cancellationMapper = cancellationMapper;
        this.settlementService = settlementService;
    }

    /** Retained for focused legacy tests; production uses the full dependency constructor. */
    public OrderLifecycleServiceImpl(RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                     DriverMapper driverMapper, DriverLocationMapper locationMapper,
                                     PassengerMapper passengerMapper, OrderAccessService orderAccessService, Clock clock) {
        this(orderMapper, eventMapper, driverMapper, locationMapper, passengerMapper, orderAccessService, clock,
                null, null, null, null, null, null);
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse cancelByPassenger(Long passengerId, String orderNo, String reason) {
        RideOrder order = orderAccessService.requirePassengerOrder(orderNo, passengerId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.ACCEPTED
                && order.getStatus() != OrderStatus.SCHEDULED) {
            if (order.getStatus() == OrderStatus.DRIVER_ARRIVED) {
                return cancelArrivedByPassenger(order, passengerId, reason);
            }
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

    @Override
    @Transactional
    public OrderTerminationResponse terminateByPassenger(Long passengerId, String orderNo, String reasonCode, String reasonDetail) {
        return terminate(orderAccessService.requirePassengerOrder(orderNo, passengerId), OrderOperatorType.passenger,
                passengerId, reasonCode, reasonDetail);
    }

    @Override
    @Transactional
    public OrderTerminationResponse terminateByDriver(Long driverId, String orderNo, String reasonCode, String reasonDetail) {
        return terminate(orderAccessService.requireDriverOrder(orderNo, driverId), OrderOperatorType.driver,
                driverId, reasonCode, reasonDetail);
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

    private OrderStatusTransitionResponse cancelArrivedByPassenger(RideOrder order, Long passengerId, String reason) {
        requireFullServices();
        PricingRuleDefinition rule = pricingRuleService.getByIdAndVersion(order.getPricingRuleId(), order.getPricingRuleVersion());
        BigDecimal fee = rule.arrivalCancelFee();
        if (fee == null || fee.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "到达后取消费配置不可用");
        }
        LocalDateTime time = now();
        String normalizedReason = normalizeReason(reason);
        if (orderMapper.cancelArrivedOrderWithFee(order.getId(), order.getVersion(), time, "passenger", normalizedReason, fee) != 1) {
            throw conflict("订单状态已变化");
        }
        writeCancellation(order, OrderStatus.CANCELLED, OrderOperatorType.passenger, OrderStatus.DRIVER_ARRIVED,
                "ARRIVAL_CANCEL", normalizedReason, fee, null, time);
        writeFixedFareItem(order, fee, time);
        settlementService.settle(order, fee, time);
        writeEvent(order.getId(), OrderEventType.CANCEL, OrderStatus.DRIVER_ARRIVED, OrderStatus.CANCELLED,
                OrderOperatorType.passenger, passengerId, time, normalizedReason);
        releaseDriver(order);
        return new OrderStatusTransitionResponse(order.getOrderNo(), OrderStatus.CANCELLED, time, null);
    }

    private OrderTerminationResponse terminate(RideOrder order, OrderOperatorType operatorType, Long operatorId,
                                               String reasonCode, String reasonDetail) {
        requireFullServices();
        requireTransition(order, OrderEventType.TERMINATE, OrderStatus.IN_SERVICE, OrderStatus.TERMINATED);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "中途结束原因不能为空");
        }
        DriverLocation location = locationMapper.selectById(order.getDriverId());
        if (location == null || location.getReceivedTime() == null || location.getReceivedTime().isBefore(now().minusSeconds(30))) {
            throw conflict("司机位置已过期，请先上报当前位置");
        }
        LocalDateTime endedTime = now();
        long actualDuration = Duration.between(order.getServiceStartedTime(), endedTime).getSeconds();
        if (actualDuration < 0) throw conflict("订单开始服务时间异常");
        RouteEstimate route = amapRouteClient.estimateDrivingRoute(new Gcj02Coordinate(order.getPickupLat().doubleValue(), order.getPickupLng().doubleValue()),
                new Gcj02Coordinate(location.getLatitude().doubleValue(), location.getLongitude().doubleValue()));
        PricingRuleDefinition rule = pricingRuleService.getByIdAndVersion(order.getPricingRuleId(), order.getPricingRuleVersion());
        FareCalculation calculation = fareCalculator.calculate(rule, route.distanceM(), actualDuration,
                order.getWaitingDurationS() == null ? 0 : order.getWaitingDurationS());
        if (orderMapper.terminateOrder(order.getId(), order.getDriverId(), order.getVersion(), endedTime, actualDuration,
                calculation.baseFare(), calculation.distanceFare(), calculation.drivingTimeFare(), calculation.waitingFare(), calculation.totalFare()) != 1) {
            throw conflict("订单状态已变化");
        }
        List<OrderFareItem> items = calculation.lines().stream().map(line -> fareItem(order, line, endedTime)).toList();
        if (fareItemMapper.insertBatch(items) != items.size()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入计费明细失败");
        writeCancellation(order, OrderStatus.TERMINATED, operatorType, OrderStatus.IN_SERVICE, reasonCode.trim(),
                normalizeReasonDetail(reasonDetail), calculation.totalFare(), location, endedTime);
        settlementService.settle(order, calculation.totalFare(), endedTime);
        writeEvent(order.getId(), OrderEventType.TERMINATE, OrderStatus.IN_SERVICE, OrderStatus.TERMINATED,
                operatorType, operatorId, endedTime, reasonCode.trim());
        releaseDriver(order);
        return new OrderTerminationResponse(order.getOrderNo(), OrderStatus.TERMINATED, endedTime, actualDuration,
                route.distanceM(), calculation.totalFare());
    }

    private void writeCancellation(RideOrder order, OrderStatus terminal, OrderOperatorType initiator, OrderStatus stage,
                                   String reasonCode, String reasonDetail, BigDecimal charged, DriverLocation location,
                                   LocalDateTime time) {
        OrderCancellation record = new OrderCancellation();
        record.setOrderId(order.getId()); record.setTerminalStatus(terminal); record.setInitiatorType(initiator);
        record.setCancellationStage(stage); record.setReasonCode(reasonCode); record.setReasonDetail(reasonDetail);
        record.setChargedAmount(charged); record.setActualEndLat(location == null ? null : location.getLatitude());
        record.setActualEndLng(location == null ? null : location.getLongitude()); record.setEndedTime(time); record.setCreatedTime(time);
        if (cancellationMapper.insert(record) != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录异常结束信息失败");
    }

    private void writeFixedFareItem(RideOrder order, BigDecimal fee, LocalDateTime time) {
        OrderFareItem item = new OrderFareItem(); item.setOrderId(order.getId()); item.setChargeType(FareChargeType.ARRIVAL_CANCEL);
        item.setRuleId(order.getPricingRuleId()); item.setRuleVersion(order.getPricingRuleVersion()); item.setActualUsage(1L);
        item.setUnitPrice(fee); item.setChargedUnits(1L); item.setAmount(fee); item.setCreatedTime(time);
        if (fareItemMapper.insert(item) != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入取消费用明细失败");
    }

    private OrderFareItem fareItem(RideOrder order, FareLine line, LocalDateTime time) {
        OrderFareItem item = new OrderFareItem(); item.setOrderId(order.getId()); item.setChargeType(line.chargeType());
        item.setRuleId(order.getPricingRuleId()); item.setRuleVersion(order.getPricingRuleVersion()); item.setTierId(line.tierId());
        item.setRangeStart(line.rangeStart()); item.setRangeEnd(line.rangeEnd()); item.setActualUsage(line.actualUsage());
        item.setUnitSize(line.unitSize()); item.setUnitPrice(line.unitPrice()); item.setChargedUnits(line.chargedUnits());
        item.setAmount(line.amount()); item.setCreatedTime(time); return item;
    }

    private void releaseDriver(RideOrder order) {
        if (driverMapper.markOnlineIfBusy(order.getDriverId()) != 1) throw conflict("司机出车状态已变化");
        locationMapper.clearActiveOrder(order.getDriverId(), order.getId());
    }

    private void requireFullServices() {
        if (pricingRuleService == null || fareCalculator == null || amapRouteClient == null || fareItemMapper == null
                || cancellationMapper == null || settlementService == null) throw new IllegalStateException("订单结算服务未配置");
    }

    private String normalizeReasonDetail(String reason) { return reason == null || reason.isBlank() ? null : reason.trim(); }

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
