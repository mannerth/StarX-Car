package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.infra.amap.AmapRouteClient;
import org.mannerth.starxcar.infra.amap.Gcj02Coordinate;
import org.mannerth.starxcar.infra.amap.RouteEstimate;
import org.mannerth.starxcar.order.pricing.FareCalculation;
import org.mannerth.starxcar.order.pricing.FareCalculator;
import org.mannerth.starxcar.order.pricing.PricingRuleDefinition;
import org.mannerth.starxcar.order.pricing.PricingRuleService;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.request.PlaceRequest;
import org.mannerth.starxcar.order.response.FareQuoteResponse;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.passenger.Passenger;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    private static final List<OrderStatus> UNFINISHED_STATUSES = List.of(
            OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PassengerMapper passengerMapper;
    private final UserMapper userMapper;
    private final RideOrderMapper rideOrderMapper;
    private final RideOrderEventMapper rideOrderEventMapper;
    private final PricingRuleService pricingRuleService;
    private final FareCalculator fareCalculator;
    private final AmapRouteClient amapRouteClient;
    private final OrderNumberGenerator orderNumberGenerator;
    private final Clock clock;

    public OrderServiceImpl(PassengerMapper passengerMapper, UserMapper userMapper, RideOrderMapper rideOrderMapper,
                            RideOrderEventMapper rideOrderEventMapper, PricingRuleService pricingRuleService,
                            FareCalculator fareCalculator, AmapRouteClient amapRouteClient,
                            OrderNumberGenerator orderNumberGenerator, Clock clock) {
        this.passengerMapper = passengerMapper;
        this.userMapper = userMapper;
        this.rideOrderMapper = rideOrderMapper;
        this.rideOrderEventMapper = rideOrderEventMapper;
        this.pricingRuleService = pricingRuleService;
        this.fareCalculator = fareCalculator;
        this.amapRouteClient = amapRouteClient;
        this.orderNumberGenerator = orderNumberGenerator;
        this.clock = clock;
    }

    @Override
    public OrderQuoteResponse quote(OrderQuoteRequest request) {
        return calculateQuote(request);
    }

    @Override
    @Transactional
    public OrderCreatedResponse create(Long passengerId, OrderQuoteRequest request) {
        Passenger passenger = passengerMapper.lockById(passengerId);
        if (passenger == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "乘客不存在");
        }
        requireActivePassenger(passengerId);
        long unfinishedCount = rideOrderMapper.selectCount(Wrappers.<RideOrder>lambdaQuery()
                .eq(RideOrder::getPassengerId, passengerId)
                .in(RideOrder::getStatus, UNFINISHED_STATUSES));
        if (unfinishedCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前存在未结束订单");
        }

        OrderQuoteResponse quote = calculateQuote(request);
        RideOrder order = buildOrder(passengerId, request, quote);
        if (rideOrderMapper.insert(order) != 1 || order.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "订单创建失败");
        }
        RideOrderEvent event = new RideOrderEvent();
        event.setOrderId(order.getId());
        event.setEventType(OrderEventType.CREATE);
        event.setToStatus(OrderStatus.PENDING);
        event.setOperatorType(OrderOperatorType.passenger);
        event.setOperatorId(passengerId);
        event.setCreatedTime(now());
        rideOrderEventMapper.insert(event);
        return new OrderCreatedResponse(order.getOrderNo(), order.getStatus(), quote);
    }

    private OrderQuoteResponse calculateQuote(OrderQuoteRequest request) {
        PricingRuleDefinition rule = pricingRuleService.getDefaultActiveRule();
        RouteEstimate route = amapRouteClient.estimateDrivingRoute(toCoordinate(request.pickup()),
                toCoordinate(request.destination()));
        FareCalculation fare = fareCalculator.calculate(rule, route.distanceM(), route.durationS(), 0);
        return new OrderQuoteResponse(rule.id(), rule.version(), route.distanceM(), route.durationS(), toFare(fare));
    }

    private RideOrder buildOrder(Long passengerId, OrderQuoteRequest request, OrderQuoteResponse quote) {
        RideOrder order = new RideOrder();
        order.setOrderNo(orderNumberGenerator.next());
        order.setPassengerId(passengerId);
        order.setPricingRuleId(quote.pricingRuleId());
        order.setPricingRuleVersion(quote.pricingRuleVersion());
        order.setStatus(OrderStatus.PENDING);
        order.setVersion(0L);
        order.setPaymentStatus("unpaid");
        copyPickup(order, request.pickup());
        copyDestination(order, request.destination());
        order.setEstimatedDistanceM(quote.estimatedDistanceM());
        order.setEstimatedDurationS(quote.estimatedDurationS());
        order.setBillingDistanceM(quote.estimatedDistanceM());
        order.setBaseFare(quote.fare().baseFare());
        order.setDistanceFare(quote.fare().distanceFare());
        order.setDrivingTimeFare(quote.fare().drivingTimeFare());
        order.setWaitingFare(quote.fare().waitingFare());
        order.setTotalFare(quote.fare().totalFare());
        order.setCreatedTime(now());
        return order;
    }

    private void requireActivePassenger(Long passengerId) {
        User user = userMapper.selectById(passengerId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
    }

    private void copyPickup(RideOrder order, PlaceRequest place) {
        order.setPickupPoiId(place.poiId());
        order.setPickupName(place.name().trim());
        order.setPickupAddress(place.address());
        order.setPickupLat(place.latitude());
        order.setPickupLng(place.longitude());
    }

    private void copyDestination(RideOrder order, PlaceRequest place) {
        order.setDestinationPoiId(place.poiId());
        order.setDestinationName(place.name().trim());
        order.setDestinationAddress(place.address());
        order.setDestinationLat(place.latitude());
        order.setDestinationLng(place.longitude());
    }

    private Gcj02Coordinate toCoordinate(PlaceRequest place) {
        return new Gcj02Coordinate(place.latitude().doubleValue(), place.longitude().doubleValue());
    }

    private FareQuoteResponse toFare(FareCalculation fare) {
        return new FareQuoteResponse(fare.baseFare(), fare.distanceFare(), fare.drivingTimeFare(), fare.waitingFare(),
                fare.totalFare());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }
}
