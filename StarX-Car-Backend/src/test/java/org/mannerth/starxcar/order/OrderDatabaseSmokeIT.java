package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverCarBinding;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.driver.DriverWorkService;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.infra.amap.AmapRouteClient;
import org.mannerth.starxcar.infra.amap.RouteEstimate;
import org.mannerth.starxcar.order.pricing.AdminPricingRuleService;
import org.mannerth.starxcar.order.pricing.PricingRuleMapper;
import org.mannerth.starxcar.order.pricing.PricingRuleService;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.request.PricingTierWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.request.PlaceRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mannerth.starxcar.passenger.Passenger;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockitoBean
    private AmapRouteClient amapRouteClient;

    private Long passengerId;
    private Long driverId;
    private Long adminId;
    private Long carId;
    private Long ruleId;
    private String orderNo;

    @Test
    void persistsAndRollsBackTheImmediateRideWorkflow() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
        lifecycleService.startService(driverUser.getId(), created.orderNo());
        RideRouteContextResponse routeContext = tripLocationService.getRouteContext(passengerUser.getId(), created.orderNo());
        assertThat(routeContext.destinationName()).isEqualTo("目的地");
        assertThat(routeContext.driverLocation().latitude()).isEqualByComparingTo("39.916527");

        completionService.complete(driverUser.getId(), created.orderNo());
        RideOrder completed = order(created.orderNo());
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

        PricingRuleResponse draft = adminPricingRuleService.create(adminUser.getId(), pricingRuleRequest("it-rule-" + suffix));
        PricingRuleResponse active = adminPricingRuleService.activate(adminUser.getId(), draft.id(),
                new PricingRuleActivationRequest(false));
        PricingRuleResponse archived = adminPricingRuleService.archive(adminUser.getId(), active.id());
        ruleId = archived.id();
        assertThat(archived.status().name()).isEqualTo("archived");
        assertThat(adminPricingRuleService.get(archived.id()).tiers()).hasSize(7);
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

    private PricingRuleWriteRequest pricingRuleRequest(String name) {
        return new PricingRuleWriteRequest(name, new BigDecimal("5.00"), 2000L, new BigDecimal("5.00"), List.of(
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
