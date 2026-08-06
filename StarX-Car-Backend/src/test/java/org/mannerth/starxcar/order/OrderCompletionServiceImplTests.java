package org.mannerth.starxcar.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.pricing.FareCalculator;
import org.mannerth.starxcar.order.pricing.PricingFixtures;
import org.mannerth.starxcar.order.pricing.PricingRuleService;
import org.mannerth.starxcar.order.pricing.PricingRuleValidator;
import org.mannerth.starxcar.order.response.OrderCompletionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderCompletionServiceImplTests {
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-05T02:30:01Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));

    private RideOrderMapper orderMapper;
    private OrderFareItemMapper fareItemMapper;
    private RideOrderEventMapper eventMapper;
    private DriverMapper driverMapper;
    private DriverLocationMapper locationMapper;
    private OrderAccessService accessService;
    private PricingRuleService pricingRuleService;
    private OrderCompletionService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(RideOrderMapper.class);
        fareItemMapper = mock(OrderFareItemMapper.class);
        eventMapper = mock(RideOrderEventMapper.class);
        driverMapper = mock(DriverMapper.class);
        locationMapper = mock(DriverLocationMapper.class);
        accessService = mock(OrderAccessService.class);
        pricingRuleService = mock(PricingRuleService.class);
        service = new OrderCompletionServiceImpl(orderMapper, fareItemMapper, eventMapper, driverMapper, locationMapper,
                accessService, pricingRuleService, new FareCalculator(new PricingRuleValidator()),
                Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completesUsingHistoricalRuleAndWritesAuditableFareItems() {
        RideOrder order = inServiceOrder();
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(pricingRuleService.getByIdAndVersion(1L, 1)).willReturn(PricingFixtures.defaultRule());
        given(orderMapper.completeOrder(10L, 2L, 7L, NOW, 1801L, new BigDecimal("5.00"),
                new BigDecimal("31.20"), new BigDecimal("6.50"), new BigDecimal("0.50"),
                new BigDecimal("43.20"))).willReturn(1);
        given(fareItemMapper.insertBatch(any())).willReturn(7);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markOnlineIfBusy(2L)).willReturn(1);

        OrderCompletionResponse result = service.complete(2L, "O1");

        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.actualDurationS()).isEqualTo(1801L);
        assertThat(result.waitingDurationS()).isEqualTo(301L);
        assertThat(result.fare().totalFare()).isEqualByComparingTo("43.20");
        org.mockito.ArgumentCaptor<List<OrderFareItem>> items = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(fareItemMapper).insertBatch(items.capture());
        assertThat(items.getValue()).hasSize(7);
        assertThat(items.getValue()).allSatisfy(item -> {
            assertThat(item.getOrderId()).isEqualTo(10L);
            assertThat(item.getRuleId()).isEqualTo(1L);
            assertThat(item.getRuleVersion()).isEqualTo(1);
        });
        assertThat(items.getValue().stream().map(OrderFareItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("43.20");
        verify(pricingRuleService, never()).getDefaultActiveRule();
        verify(locationMapper).clearActiveOrder(2L, 10L);
    }

    @Test
    void truncatesCompletionTimeToTheDatabaseSecondPrecision() {
        service = new OrderCompletionServiceImpl(orderMapper, fareItemMapper, eventMapper, driverMapper,
                locationMapper, accessService, pricingRuleService, new FareCalculator(new PricingRuleValidator()),
                Clock.fixed(Instant.parse("2026-08-05T02:30:01.900Z"), ZoneId.of("Asia/Shanghai")));
        RideOrder order = inServiceOrder();
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(pricingRuleService.getByIdAndVersion(1L, 1)).willReturn(PricingFixtures.defaultRule());
        given(orderMapper.completeOrder(10L, 2L, 7L, NOW, 1801L, new BigDecimal("5.00"),
                new BigDecimal("31.20"), new BigDecimal("6.50"), new BigDecimal("0.50"),
                new BigDecimal("43.20"))).willReturn(1);
        given(fareItemMapper.insertBatch(any())).willReturn(7);
        given(eventMapper.insert(any(RideOrderEvent.class))).willReturn(1);
        given(driverMapper.markOnlineIfBusy(2L)).willReturn(1);

        OrderCompletionResponse result = service.complete(2L, "O1");

        assertThat(result.completedTime()).isEqualTo(NOW);
        verify(orderMapper).completeOrder(10L, 2L, 7L, NOW, 1801L, new BigDecimal("5.00"),
                new BigDecimal("31.20"), new BigDecimal("6.50"), new BigDecimal("0.50"),
                new BigDecimal("43.20"));
    }

    @Test
    void staleCompletionConditionDoesNotWriteFareItemsOrReleaseDriver() {
        RideOrder order = inServiceOrder();
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(pricingRuleService.getByIdAndVersion(1L, 1)).willReturn(PricingFixtures.defaultRule());
        given(orderMapper.completeOrder(anyLong(), anyLong(), anyLong(), any(), anyLong(), any(), any(), any(), any(), any()))
                .willReturn(0);

        assertConflict(() -> service.complete(2L, "O1"));

        verify(fareItemMapper, never()).insertBatch(any());
        verify(eventMapper, never()).insert(any(RideOrderEvent.class));
        verify(driverMapper, never()).markOnlineIfBusy(anyLong());
    }

    @Test
    void fareItemFailurePreventsEventAndDriverRelease() {
        RideOrder order = inServiceOrder();
        given(accessService.requireDriverOrder("O1", 2L)).willReturn(order);
        given(pricingRuleService.getByIdAndVersion(1L, 1)).willReturn(PricingFixtures.defaultRule());
        given(orderMapper.completeOrder(anyLong(), anyLong(), anyLong(), any(), anyLong(), any(), any(), any(), any(), any()))
                .willReturn(1);
        given(fareItemMapper.insertBatch(any())).willReturn(0);

        assertThatThrownBy(() -> service.complete(2L, "O1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(eventMapper, never()).insert(any(RideOrderEvent.class));
        verify(driverMapper, never()).markOnlineIfBusy(anyLong());
    }

    private RideOrder inServiceOrder() {
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O1");
        order.setDriverId(2L);
        order.setStatus(OrderStatus.IN_SERVICE);
        order.setVersion(7L);
        order.setPricingRuleId(1L);
        order.setPricingRuleVersion(1);
        order.setBillingDistanceM(12_001L);
        order.setWaitingDurationS(301L);
        order.setServiceStartedTime(NOW.minusSeconds(1801));
        return order;
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
