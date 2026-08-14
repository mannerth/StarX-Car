package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderDriverResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OrderQueryServiceImplTests {
    private RideOrderMapper rideOrderMapper;
    private RideOrderEventMapper eventMapper;
    private OrderAccessService accessService;
    private DriverMapper driverMapper;
    private CarMapper carMapper;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        rideOrderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        accessService = mock(OrderAccessService.class);
        driverMapper = mock(DriverMapper.class);
        carMapper = mock(CarMapper.class);
        service = new OrderQueryServiceImpl(rideOrderMapper, eventMapper, accessService, driverMapper, carMapper,
                mock(PassengerMapper.class));
    }

    @Test
    void mapsPassengerPageWithStablePagingMetadata() {
        Page<RideOrder> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(order("O1", OrderStatus.PENDING)));
        given(rideOrderMapper.selectPage(any(), any())).willReturn(page);

        OrderPageResponse result = service.listPassengerOrders(1L, 1, 20, OrderStatus.PENDING);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.orderNo()).isEqualTo("O1");
            assertThat(summary.estimatedFare()).isTrue();
        });
    }

    @Test
    void returnsDetailsWithOrderedEventsOnlyForAuthorizedOrder() {
        RideOrder order = order("O2", OrderStatus.COMPLETED);
        given(accessService.requirePassengerOrder("O2", 1L)).willReturn(order);
        RideOrderEvent event = new RideOrderEvent();
        event.setEventType(OrderEventType.CREATE);
        event.setToStatus(OrderStatus.PENDING);
        event.setOperatorType(OrderOperatorType.passenger);
        event.setCreatedTime(LocalDateTime.of(2026, 8, 4, 20, 0));
        given(eventMapper.selectList(any())).willReturn(List.of(event));

        OrderDetailResponse result = service.getOrder(1L, UserType.passenger, "O2");

        assertThat(result.orderNo()).isEqualTo("O2");
        assertThat(result.estimatedFare()).isFalse();
        assertThat(result.events()).singleElement().satisfies(item -> {
            assertThat(item.eventType()).isEqualTo(OrderEventType.CREATE);
            assertThat(item.toStatus()).isEqualTo(OrderStatus.PENDING);
        });
    }

    @Test
    void exposesFullPhoneRatingAndVehicleInfoForAssignedDriver() {
        RideOrder order = order("O3", OrderStatus.ACCEPTED);
        order.setDriverId(2L);
        order.setCarId(9L);
        given(accessService.requirePassengerOrder("O3", 1L)).willReturn(order);
        given(eventMapper.selectList(any())).willReturn(List.of());
        Driver driver = new Driver();
        driver.setId(2L);
        driver.setPhoneNumber("13800138000");
        driver.setDisplayPhone("138****0000");
        driver.setRating(new BigDecimal("4.8"));
        given(driverMapper.selectById(2L)).willReturn(driver);
        Car car = new Car();
        car.setId(9L);
        car.setCarNumber("京B12345");
        car.setBrand("大众");
        car.setColor("黑色");
        car.setCarType("economy");
        given(carMapper.selectById(9L)).willReturn(car);

        OrderDetailResponse result = service.getOrder(1L, UserType.passenger, "O3");

        OrderDriverResponse assigned = result.driver();
        assertThat(assigned).isNotNull();
        assertThat(assigned.phoneNumber()).isEqualTo("13800138000");
        assertThat(assigned.displayPhone()).isEqualTo("138****0000");
        assertThat(assigned.rating()).isEqualByComparingTo("4.8");
        assertThat(assigned.carNumber()).isEqualTo("京B12345");
        assertThat(assigned.carBrand()).isEqualTo("大众");
        assertThat(assigned.carColor()).isEqualTo("黑色");
        assertThat(assigned.carType()).isEqualTo("economy");
    }

    private RideOrder order(String orderNo, OrderStatus status) {
        RideOrder order = new RideOrder();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setPickupName("天安门");
        order.setDestinationName("外滩");
        order.setEstimatedDistanceM(12_001L);
        order.setEstimatedDurationS(1_801L);
        order.setBillingDistanceM(12_001L);
        order.setBaseFare(new BigDecimal("5.00"));
        order.setDistanceFare(new BigDecimal("31.20"));
        order.setDrivingTimeFare(new BigDecimal("6.50"));
        order.setWaitingFare(BigDecimal.ZERO);
        order.setTotalFare(new BigDecimal("42.70"));
        order.setCreatedTime(LocalDateTime.of(2026, 8, 4, 20, 0));
        return order;
    }
}

