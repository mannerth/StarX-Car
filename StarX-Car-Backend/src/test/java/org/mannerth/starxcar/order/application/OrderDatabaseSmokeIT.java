package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.car.domain.Car;
import org.mannerth.starxcar.car.infrastructure.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.application.DriverWorkService;
import org.mannerth.starxcar.driver.domain.Driver;
import org.mannerth.starxcar.driver.domain.DriverCarBinding;
import org.mannerth.starxcar.driver.infrastructure.DriverCarBindingMapper;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.infrastructure.amap.AmapRouteClient;
import org.mannerth.starxcar.infrastructure.amap.RouteEstimate;
import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderFareItem;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.domain.RoundingMode;
import org.mannerth.starxcar.order.infrastructure.DriverLocationMapper;
import org.mannerth.starxcar.order.infrastructure.OrderFareItemMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.pricing.application.AdminPricingRuleService;
import org.mannerth.starxcar.order.pricing.application.PricingRuleService;
import org.mannerth.starxcar.order.pricing.infrastructure.PricingRuleMapper;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.request.PricingTierWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;
import org.mannerth.starxcar.order.rating.application.RatingService;
import org.mannerth.starxcar.order.rating.domain.OrderRating;
import org.mannerth.starxcar.order.rating.infrastructure.OrderRatingMapper;
import org.mannerth.starxcar.order.rating.request.RatingSubmitRequest;
import org.mannerth.starxcar.order.rating.response.OrderRatingResponse;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.request.PlaceRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;




/**
 * Explicit-only database smoke test. Run with the runtime .env config; the enclosing transaction rolls back all rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrderDatabaseSmokeIT {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PassengerMapper passengerMapper;
    @Autowired
    private DriverMapper driverMapper;
    @Autowired
    private CarMapper carMapper;
    @Autowired
    private DriverCarBindingMapper bindingMapper;
    @Autowired
    private DriverLocationMapper locationMapper;
    @Autowired
    private RideOrderMapper orderMapper;
    @Autowired
    private RideOrderEventMapper eventMapper;
    @Autowired
    private OrderFareItemMapper fareItemMapper;
    @Autowired
    private DriverWorkService driverWorkService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DriverDispatchService dispatchService;
    @Autowired
    private OrderLifecycleService lifecycleService;
    @Autowired
    private PassengerTripLocationService tripLocationService;
    @Autowired
    private OrderCompletionService completionService;
    @Autowired
    private OrderQueryService queryService;
    @Autowired
    private PricingRuleService pricingRuleService;
    @Autowired
    private AdminPricingRuleService adminPricingRuleService;
    @Autowired
    private PricingRuleMapper pricingRuleMapper;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private OrderRatingMapper ratingMapper;
    @Autowired
    private ReservationDispatchService reservationDispatchService;

    @MockitoBean
    private AmapRouteClient amapRouteClient;

    private Long passengerId;
    private Long driverId;
    private Long adminId;
    private Long carId;
    private Long ruleId;
    private Long orderId;
    private String orderNo;

    @Test
    void persistsAndRollsBackTheImmediateRideWorkflow() {
        String suffix = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        User passengerUser = user("it-passenger-" + suffix, UserType.passenger);
        User driverUser = user("it-driver-" + suffix, UserType.driver);
        User adminUser = user("it-admin-" + suffix, UserType.admin);
        insert(passengerUser);
        insert(driverUser);
        insert(adminUser);
        passengerId = passengerUser.getId();
        driverId = driverUser.getId();
        adminId = adminUser.getId();
        insertPassenger(passengerUser.getId(), "139" + suffix.substring(0, 8));
        Car car = insertCar(suffix);
        carId = car.getId();
        insertDriver(driverUser.getId(), car.getId(), "138" + suffix.substring(0, 8));
        insertBinding(driverUser.getId(), car.getId());

        LocalDateTime locationTime = LocalDateTime.now(BUSINESS_ZONE);
        driverWorkService.reportLocation(driverUser.getId(), new DriverLocationRequest(
                new BigDecimal("39.916527"), new BigDecimal("116.397128"), new BigDecimal("8.50"),
                new BigDecimal("12.30"), new BigDecimal("180.00"), locationTime));
        assertThat(locationMapper.selectById(driverUser.getId())).isNotNull();
        assertThat(pricingRuleService.getDefaultActiveRule().tiers()).hasSize(7);

        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));
        OrderQuoteRequest request = quoteRequest();
        OrderQuoteResponse quote = orderService.quote(request);
        OrderCreatedResponse created = orderService.create(passengerUser.getId(), request);
        orderNo = created.orderNo();
        assertThat(created.quote()).isEqualTo(quote);

        dispatchService.acceptOrder(driverUser.getId(), created.orderNo());
        RideOrder accepted = order(created.orderNo());
        assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(accepted.getDriverId()).isEqualTo(driverUser.getId());
        assertThat(locationMapper.selectById(driverUser.getId()).getActiveOrderId()).isEqualTo(accepted.getId());
        assertThat(tripLocationService.getDriverLocation(passengerUser.getId(), created.orderNo()).stale()).isFalse();

        lifecycleService.markDriverArrived(driverUser.getId(), created.orderNo());
        RideOrder arrived = order(created.orderNo());
        // Keep the smoke test fast while exercising the post-free waiting-time calculation branch.
        arrived.setDriverArrivedTime(LocalDateTime.now(BUSINESS_ZONE).minusSeconds(301));
        assertThat(orderMapper.updateById(arrived)).isEqualTo(1);
        String passengerTail = suffix.substring(4, 8);
        lifecycleService.startService(driverUser.getId(), created.orderNo(), passengerTail);
        RideRouteContextResponse routeContext = tripLocationService.getRouteContext(passengerUser.getId(), created.orderNo());
        assertThat(routeContext.destinationName()).isEqualTo("目的地");
        assertThat(routeContext.driverLocation().latitude()).isEqualByComparingTo("39.916527");

        completionService.complete(driverUser.getId(), created.orderNo());
        RideOrder completed = order(created.orderNo());
        orderId = completed.getId();
        List<OrderFareItem> fareItems = fareItemMapper.selectList(Wrappers.<OrderFareItem>lambdaQuery()
                .eq(OrderFareItem::getOrderId, completed.getId()));
        BigDecimal itemTotal = fareItems.stream().map(OrderFareItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(fareItems).isNotEmpty();
        assertThat(itemTotal).isEqualByComparingTo(completed.getTotalFare());
        assertThat(eventMapper.selectCount(Wrappers.<RideOrderEvent>lambdaQuery()
                .eq(RideOrderEvent::getOrderId, completed.getId()))).isEqualTo(5);
        assertThat(driverMapper.selectById(driverUser.getId()).getWorkStatus()).isEqualTo("online");
        assertThat(locationMapper.selectById(driverUser.getId()).getActiveOrderId()).isNull();
        OrderDetailResponse passengerDetail = queryService.getOrder(passengerUser.getId(), UserType.passenger, created.orderNo());
        OrderDetailResponse driverDetail = queryService.getOrder(driverUser.getId(), UserType.driver, created.orderNo());
        assertThat(passengerDetail.fare().totalFare()).isEqualByComparingTo(completed.getTotalFare());
        assertThat(driverDetail.events()).hasSize(5);

        OrderRatingResponse submitted = ratingService.submit(passengerUser.getId(), created.orderNo(),
                new RatingSubmitRequest(5, "烟测评价"));
        assertThat(submitted.rated()).isTrue();
        assertThat(submitted.rating()).isEqualTo(5);
        OrderRating persistedRating = ratingMapper.selectOne(Wrappers.<OrderRating>lambdaQuery()
                .eq(OrderRating::getOrderId, completed.getId()));
        assertThat(persistedRating).isNotNull();
        assertThat(persistedRating.getPassengerId()).isEqualTo(passengerUser.getId());
        assertThat(persistedRating.getDriverId()).isEqualTo(driverUser.getId());
        assertThat(persistedRating.getContent()).isEqualTo("烟测评价");
        Map<String, Object> aggregate = driverMapper.selectRatingAggregate(driverUser.getId());
        assertThat(new BigDecimal(aggregate.get("rating").toString())).isEqualByComparingTo("5.0");
        assertThat(new BigDecimal(aggregate.get("rating_sum").toString())).isEqualByComparingTo("5.00");
        assertThat(((Number) aggregate.get("rating_count")).intValue()).isEqualTo(1);
        assertThat(ratingService.getOrderRating(passengerUser.getId(), UserType.passenger, created.orderNo()).rated())
                .isTrue();
        assertThat(ratingService.listDriverRatings(driverUser.getId(), 1, 20).records()).hasSize(1);
        assertThatThrownBy(() -> ratingService.submit(passengerUser.getId(), created.orderNo(),
                new RatingSubmitRequest(4, "重复评价")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        PricingRuleResponse draft = adminPricingRuleService.create(adminUser.getId(), pricingRuleRequest("it-rule-" + suffix));
        PricingRuleResponse active = adminPricingRuleService.activate(adminUser.getId(), draft.id(),
                new PricingRuleActivationRequest(false));
        PricingRuleResponse archived = adminPricingRuleService.archive(adminUser.getId(), active.id());
        ruleId = archived.id();
        assertThat(archived.status().name()).isEqualTo("archived");
        assertThat(adminPricingRuleService.get(archived.id()).tiers()).hasSize(7);
    }

    @Test
    void persistsAndRollsBackReservationWorkflow() {
        String suffix = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        User passengerUser = user("it-res-pass-" + suffix, UserType.passenger);
        User driverUser = user("it-res-driver-" + suffix, UserType.driver);
        User adminUser = user("it-res-admin-" + suffix, UserType.admin);
        insert(passengerUser);
        insert(driverUser);
        insert(adminUser);
        passengerId = passengerUser.getId();
        driverId = driverUser.getId();
        adminId = adminUser.getId();
        insertPassenger(passengerUser.getId(), "137" + suffix.substring(0, 8));
        Car car = insertCar("RES" + suffix.substring(0, 6));
        carId = car.getId();
        insertDriver(driverUser.getId(), car.getId(), "136" + suffix.substring(0, 8));
        insertBinding(driverUser.getId(), car.getId());
        reportLocation(driverUser.getId());

        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));

        LocalDateTime pickup = LocalDateTime.now(BUSINESS_ZONE).plusHours(2).withNano(0);
        OrderQuoteRequest reservationA = reservationRequest(pickup);
        OrderCreatedResponse createdA = orderService.create(passengerUser.getId(), reservationA);
        orderNo = createdA.orderNo();
        assertThat(createdA.status()).isEqualTo(OrderStatus.SCHEDULED);
        RideOrder scheduledA = order(createdA.orderNo());
        assertThat(scheduledA.getOrderType()).isEqualTo(OrderType.SCHEDULED);
        assertThat(scheduledA.getScheduledPickupTime()).isEqualTo(pickup);
        assertThat(scheduledA.getDispatchTime()).isNull();
        assertThat(eventMapper.selectCount(Wrappers.<RideOrderEvent>lambdaQuery()
                .eq(RideOrderEvent::getOrderId, scheduledA.getId())
                .eq(RideOrderEvent::getEventType, OrderEventType.CREATE)
                .eq(RideOrderEvent::getToStatus, OrderStatus.SCHEDULED))).isEqualTo(1);

        assertThatThrownBy(() -> orderService.create(passengerUser.getId(), reservationRequest(pickup.plusHours(1))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        OrderCreatedResponse instant = orderService.create(passengerUser.getId(), quoteRequest());
        assertThat(instant.status()).isEqualTo(OrderStatus.PENDING);
        lifecycleService.cancelByPassenger(passengerUser.getId(), instant.orderNo(), null);
        assertThat(order(instant.orderNo()).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        lifecycleService.cancelByPassenger(passengerUser.getId(), createdA.orderNo(), "计划变更");
        assertThat(order(createdA.orderNo()).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        LocalDateTime duePickup = LocalDateTime.now(BUSINESS_ZONE).plusHours(3).withNano(0);
        OrderCreatedResponse createdB = orderService.create(passengerUser.getId(), reservationRequest(duePickup));
        RideOrder scheduledB = order(createdB.orderNo());
        scheduledB.setScheduledPickupTime(LocalDateTime.now(BUSINESS_ZONE).minusMinutes(1).withNano(0));
        assertThat(orderMapper.updateById(scheduledB)).isEqualTo(1);
        assertThat(reservationDispatchService.dispatchDueReservations()).isEqualTo(1);
        RideOrder pendingB = order(createdB.orderNo());
        assertThat(pendingB.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(pendingB.getDispatchTime()).isNotNull();
        assertThat(eventMapper.selectCount(Wrappers.<RideOrderEvent>lambdaQuery()
                .eq(RideOrderEvent::getOrderId, pendingB.getId())
                .eq(RideOrderEvent::getEventType, OrderEventType.DISPATCH))).isEqualTo(1);

        reportLocation(driverUser.getId());
        OrderPageResponse pending = dispatchService.listPendingOrders(driverUser.getId(), 1, 20);
        assertThat(pending.records()).anyMatch(summary -> summary.orderType() == OrderType.SCHEDULED
                && summary.orderNo().equals(createdB.orderNo()) && summary.scheduledPickupTime() != null);
        dispatchService.acceptOrder(driverUser.getId(), createdB.orderNo());
        orderId = order(createdB.orderNo()).getId();
        assertThat(order(createdB.orderNo()).getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        lifecycleService.cancelByDriver(driverUser.getId(), createdB.orderNo(), "不去了");
        assertThat(order(createdB.orderNo()).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        OrderCreatedResponse createdC = orderService.create(passengerUser.getId(), reservationRequest(duePickup.plusHours(2)));
        RideOrder scheduledC = order(createdC.orderNo());
        scheduledC.setScheduledPickupTime(LocalDateTime.now(BUSINESS_ZONE).minusMinutes(1).withNano(0));
        assertThat(orderMapper.updateById(scheduledC)).isEqualTo(1);
        assertThat(reservationDispatchService.dispatchDueReservations()).isEqualTo(1);
        RideOrder pendingC = order(createdC.orderNo());
        assertThat(pendingC.getStatus()).isEqualTo(OrderStatus.PENDING);
        pendingC.setScheduledPickupTime(LocalDateTime.now(BUSINESS_ZONE).minusMinutes(40).withNano(0));
        assertThat(orderMapper.updateById(pendingC)).isEqualTo(1);
        assertThat(reservationDispatchService.expireStaleReservations()).isEqualTo(1);
        RideOrder expiredC = order(createdC.orderNo());
        assertThat(expiredC.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expiredC.getCancelledBy()).isEqualTo("system");
        assertThat(expiredC.getCancelReason()).contains("30分钟");
        assertThat(eventMapper.selectCount(Wrappers.<RideOrderEvent>lambdaQuery()
                .eq(RideOrderEvent::getOrderId, expiredC.getId())
                .eq(RideOrderEvent::getEventType, OrderEventType.CANCEL)
                .eq(RideOrderEvent::getOperatorType, OrderOperatorType.system))).isEqualTo(1);
    }

    @AfterTransaction
    void rollsBackAllTemporaryBusinessRows() {
        assertThat(userMapper.selectById(passengerId)).isNull();
        assertThat(userMapper.selectById(driverId)).isNull();
        assertThat(userMapper.selectById(adminId)).isNull();
        assertThat(passengerMapper.selectById(passengerId)).isNull();
        assertThat(driverMapper.selectById(driverId)).isNull();
        assertThat(carMapper.selectById(carId)).isNull();
        assertThat(locationMapper.selectById(driverId)).isNull();
        assertThat(orderMapper.selectCount(Wrappers.<RideOrder>lambdaQuery().eq(RideOrder::getOrderNo, orderNo)))
                .isZero();
        assertThat(pricingRuleMapper.selectById(ruleId)).isNull();
        assertThat(ratingMapper.selectCount(Wrappers.<OrderRating>lambdaQuery()
                .eq(OrderRating::getOrderId, orderId))).isZero();
    }

    private void insert(User user) {
        assertThat(userMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();
    }

    private User user(String username, UserType type) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$7EqJtq98hPqEX7fNZaFWoO5tHIuu77eyf8h9b5RvY1Gz9PzE0D0Wq");
        user.setType(type);
        user.setStatus("active");
        return user;
    }

    private void insertPassenger(Long userId, String phoneNumber) {
        Passenger passenger = new Passenger();
        passenger.setId(userId);
        passenger.setPhoneNumber(phoneNumber);
        assertThat(passengerMapper.insert(passenger)).isEqualTo(1);
    }

    private Car insertCar(String suffix) {
        Car car = new Car();
        String serial = suffix.substring(0, 6).toUpperCase();
        car.setLicenseNumber("IT-LIC-" + suffix);
        car.setCarNumber("京T" + serial);
        car.setCarType("economy");
        car.setBrand("Smoke");
        car.setColor("black");
        car.setPlateProvince("京");
        car.setPlateLetter("T");
        car.setPlateSerial(serial);
        car.setRegistrationPlace("test");
        car.setSeatCount(5);
        car.setVehicleStatus("active");
        assertThat(carMapper.insert(car)).isEqualTo(1);
        assertThat(car.getId()).isNotNull();
        return car;
    }

    private void insertDriver(Long userId, Long carId, String phoneNumber) {
        Driver driver = new Driver();
        driver.setId(userId);
        driver.setPhoneNumber(phoneNumber);
        driver.setLicenseNumber("IT-DL-" + userId);
        driver.setRating(new BigDecimal("5.0"));
        driver.setCarId(carId);
        driver.setApprovalStatus("approved");
        driver.setWorkStatus("online");
        assertThat(driverMapper.insert(driver)).isEqualTo(1);
    }

    private void insertBinding(Long driverId, Long carId) {
        DriverCarBinding binding = new DriverCarBinding();
        binding.setDriverId(driverId);
        binding.setCarId(carId);
        binding.setBindTime(LocalDateTime.now(BUSINESS_ZONE));
        binding.setStatus("active");
        assertThat(bindingMapper.insert(binding)).isEqualTo(1);
    }

    private RideOrder order(String orderNo) {
        RideOrder order = orderMapper.selectOne(Wrappers.<RideOrder>lambdaQuery().eq(RideOrder::getOrderNo, orderNo));
        assertThat(order).isNotNull();
        return order;
    }

    private OrderQuoteRequest quoteRequest() {
        return new OrderQuoteRequest(
                new PlaceRequest("pickup", "上车点", "测试地址", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "目的地", "测试地址", new BigDecimal("39.926527"), new BigDecimal("116.407128")));
    }

    private OrderQuoteRequest reservationRequest(LocalDateTime scheduledPickupTime) {
        return new OrderQuoteRequest(
                new PlaceRequest("pickup", "上车点", "测试地址", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "目的地", "测试地址", new BigDecimal("39.926527"), new BigDecimal("116.407128")),
                OrderType.SCHEDULED, scheduledPickupTime);
    }

    private void reportLocation(Long driverId) {
        driverWorkService.reportLocation(driverId, new DriverLocationRequest(
                new BigDecimal("39.916527"), new BigDecimal("116.397128"), new BigDecimal("8.50"),
                new BigDecimal("12.30"), new BigDecimal("180.00"), LocalDateTime.now(BUSINESS_ZONE)));
    }

    private PricingRuleWriteRequest pricingRuleRequest(String name) {
        return new PricingRuleWriteRequest(name, new BigDecimal("5.00"), 2000L, new BigDecimal("5.00"), new BigDecimal("5.00"), List.of(
                tier(FareChargeType.DISTANCE, 0, 2000L, 1000, "0.00", 10),
                tier(FareChargeType.DISTANCE, 2000, 12000L, 1000, "3.00", 20),
                tier(FareChargeType.DISTANCE, 12000, null, 1000, "1.20", 30),
                tier(FareChargeType.DRIVING_TIME, 0, 1800L, 60, "0.20", 10),
                tier(FareChargeType.DRIVING_TIME, 1800, null, 60, "0.50", 20),
                tier(FareChargeType.WAITING_TIME, 0, 300L, 60, "0.00", 10),
                tier(FareChargeType.WAITING_TIME, 300, null, 60, "0.50", 20)));
    }

    private PricingTierWriteRequest tier(FareChargeType type, long start, Long end, long unit, String price, int sort) {
        return new PricingTierWriteRequest(type, start, end, unit, new BigDecimal(price), RoundingMode.CEIL, sort);
    }
}
