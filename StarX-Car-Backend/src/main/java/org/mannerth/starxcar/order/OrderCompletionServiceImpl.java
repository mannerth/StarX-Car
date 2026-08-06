package org.mannerth.starxcar.order;

import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.pricing.FareCalculation;
import org.mannerth.starxcar.order.pricing.FareCalculator;
import org.mannerth.starxcar.order.pricing.FareLine;
import org.mannerth.starxcar.order.pricing.PricingRuleDefinition;
import org.mannerth.starxcar.order.pricing.PricingRuleService;
import org.mannerth.starxcar.order.response.FareQuoteResponse;
import org.mannerth.starxcar.order.response.OrderCompletionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OrderCompletionServiceImpl implements OrderCompletionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RideOrderMapper orderMapper;
    private final OrderFareItemMapper fareItemMapper;
    private final RideOrderEventMapper eventMapper;
    private final DriverMapper driverMapper;
    private final DriverLocationMapper locationMapper;
    private final OrderAccessService orderAccessService;
    private final PricingRuleService pricingRuleService;
    private final FareCalculator fareCalculator;
    private final Clock clock;

    public OrderCompletionServiceImpl(RideOrderMapper orderMapper, OrderFareItemMapper fareItemMapper,
                                      RideOrderEventMapper eventMapper, DriverMapper driverMapper,
                                      DriverLocationMapper locationMapper, OrderAccessService orderAccessService,
                                      PricingRuleService pricingRuleService, FareCalculator fareCalculator, Clock clock) {
        this.orderMapper = orderMapper;
        this.fareItemMapper = fareItemMapper;
        this.eventMapper = eventMapper;
        this.driverMapper = driverMapper;
        this.locationMapper = locationMapper;
        this.orderAccessService = orderAccessService;
        this.pricingRuleService = pricingRuleService;
        this.fareCalculator = fareCalculator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrderCompletionResponse complete(Long driverId, String orderNo) {
        RideOrder order = orderAccessService.requireDriverOrder(orderNo, driverId);
        if (!OrderStateMachine.allows(order.getStatus(), OrderEventType.COMPLETE, OrderStatus.COMPLETED)) {
            throw conflict("订单当前不可完成");
        }
        if (order.getServiceStartedTime() == null || order.getPricingRuleId() == null || order.getPricingRuleVersion() == null
                || order.getBillingDistanceM() == null) {
            throw conflict("订单缺少结算所需快照数据");
        }
        LocalDateTime completedTime = now();
        long actualDurationS = Duration.between(order.getServiceStartedTime(), completedTime).getSeconds();
        if (actualDurationS < 0) {
            throw conflict("订单开始服务时间异常");
        }
        long waitingDurationS = order.getWaitingDurationS() == null ? 0 : order.getWaitingDurationS();
        if (waitingDurationS < 0) {
            throw conflict("订单等待时间异常");
        }
        PricingRuleDefinition rule = pricingRuleService.getByIdAndVersion(order.getPricingRuleId(), order.getPricingRuleVersion());
        FareCalculation calculation = fareCalculator.calculate(rule, order.getBillingDistanceM(), actualDurationS, waitingDurationS);
        if (orderMapper.completeOrder(order.getId(), driverId, order.getVersion(), completedTime, actualDurationS,
                calculation.baseFare(), calculation.distanceFare(), calculation.drivingTimeFare(), calculation.waitingFare(),
                calculation.totalFare()) != 1) {
            throw conflict("订单状态已变化");
        }
        List<OrderFareItem> items = calculation.lines().stream().map(line -> toFareItem(order, line, completedTime)).toList();
        if (fareItemMapper.insertBatch(items) != items.size()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入计费明细失败");
        }
        writeCompleteEvent(order.getId(), driverId, completedTime);
        if (driverMapper.markOnlineIfBusy(driverId) != 1) {
            throw conflict("司机出车状态已变化");
        }
        locationMapper.clearActiveOrder(driverId, order.getId());
        return new OrderCompletionResponse(order.getOrderNo(), OrderStatus.COMPLETED, completedTime, actualDurationS,
                waitingDurationS, new FareQuoteResponse(calculation.baseFare(), calculation.distanceFare(),
                        calculation.drivingTimeFare(), calculation.waitingFare(), calculation.totalFare()));
    }

    private OrderFareItem toFareItem(RideOrder order, FareLine line, LocalDateTime createdTime) {
        OrderFareItem item = new OrderFareItem();
        item.setOrderId(order.getId());
        item.setChargeType(line.chargeType());
        item.setRuleId(order.getPricingRuleId());
        item.setRuleVersion(order.getPricingRuleVersion());
        item.setTierId(line.tierId());
        item.setRangeStart(line.rangeStart());
        item.setRangeEnd(line.rangeEnd());
        item.setActualUsage(line.actualUsage());
        item.setUnitSize(line.unitSize());
        item.setUnitPrice(line.unitPrice());
        item.setChargedUnits(line.chargedUnits());
        item.setAmount(line.amount());
        item.setCreatedTime(createdTime);
        return item;
    }

    private void writeCompleteEvent(Long orderId, Long driverId, LocalDateTime completedTime) {
        RideOrderEvent event = new RideOrderEvent();
        event.setOrderId(orderId);
        event.setEventType(OrderEventType.COMPLETE);
        event.setFromStatus(OrderStatus.IN_SERVICE);
        event.setToStatus(OrderStatus.COMPLETED);
        event.setOperatorType(OrderOperatorType.driver);
        event.setOperatorId(driverId);
        event.setCreatedTime(completedTime);
        if (eventMapper.insert(event) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录完成事件失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
