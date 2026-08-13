package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
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
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        rideOrderMapper = mock(RideOrderMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        accessService = mock(OrderAccessService.class);
        service = new OrderQueryServiceImpl(rideOrderMapper, eventMapper, accessService, mock(DriverMapper.class),
                mock(CarMapper.class), mock(PassengerMapper.class));
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

