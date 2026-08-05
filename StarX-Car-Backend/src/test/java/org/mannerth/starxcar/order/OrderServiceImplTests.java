package org.mannerth.starxcar.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.infra.amap.AmapRouteClient;
import org.mannerth.starxcar.infra.amap.RouteEstimate;
import org.mannerth.starxcar.order.pricing.FareCalculator;
import org.mannerth.starxcar.order.pricing.PricingFixtures;
import org.mannerth.starxcar.order.pricing.PricingRuleService;
import org.mannerth.starxcar.order.pricing.PricingRuleValidator;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.request.PlaceRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.passenger.Passenger;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTests {
    private PassengerMapper passengerMapper;
    private UserMapper userMapper;
    private RideOrderMapper rideOrderMapper;
    private RideOrderEventMapper rideOrderEventMapper;
    private PricingRuleService pricingRuleService;
    private AmapRouteClient amapRouteClient;
    private OrderService service;

    @BeforeEach
    void setUp() {
        passengerMapper = mock(PassengerMapper.class);
        userMapper = mock(UserMapper.class);
        rideOrderMapper = mock(RideOrderMapper.class);
        rideOrderEventMapper = mock(RideOrderEventMapper.class);
        pricingRuleService = mock(PricingRuleService.class);
        amapRouteClient = mock(AmapRouteClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC);
        service = new OrderServiceImpl(passengerMapper, userMapper, rideOrderMapper, rideOrderEventMapper,
                pricingRuleService, new FareCalculator(new PricingRuleValidator()), amapRouteClient,
                new OrderNumberGenerator(clock), clock);
    }

    @Test
    void quoteUsesServerRouteAndDefaultRule() {
        given(pricingRuleService.getDefaultActiveRule()).willReturn(PricingFixtures.defaultRule());
        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));

        OrderQuoteResponse quote = service.quote(request());

        assertThat(quote.pricingRuleId()).isEqualTo(1L);
        assertThat(quote.estimatedDistanceM()).isEqualTo(12_001);
        assertThat(quote.fare().totalFare()).isEqualByComparingTo("42.70");
        verify(amapRouteClient).estimateDrivingRoute(any(), any());
    }

    @Test
    void createLocksPassengerAndWritesOrderWithCreateEvent() {
        Passenger passenger = new Passenger();
        passenger.setId(1L);
        User user = new User();
        user.setId(1L);
        user.setStatus("active");
        given(passengerMapper.lockById(1L)).willReturn(passenger);
        given(userMapper.selectById(1L)).willReturn(user);
        given(rideOrderMapper.selectCount(any())).willReturn(0L);
        given(pricingRuleService.getDefaultActiveRule()).willReturn(PricingFixtures.defaultRule());
        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));
        when(rideOrderMapper.insert(any(RideOrder.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, RideOrder.class).setId(42L);
            return 1;
        });
        given(rideOrderEventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderCreatedResponse created = service.create(1L, request());

        assertThat(created.orderNo()).startsWith("O20260804200000000");
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.quote().fare().totalFare()).isEqualByComparingTo("42.70");
        org.mockito.ArgumentCaptor<RideOrder> orderCaptor = org.mockito.ArgumentCaptor.forClass(RideOrder.class);
        org.mockito.ArgumentCaptor<RideOrderEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(rideOrderMapper).insert(orderCaptor.capture());
        verify(rideOrderEventMapper).insert(eventCaptor.capture());
        assertThat(orderCaptor.getValue().getPassengerId()).isEqualTo(1L);
        assertThat(orderCaptor.getValue().getBillingDistanceM()).isEqualTo(12_001);
        assertThat(orderCaptor.getValue().getTotalFare()).isEqualByComparingTo("42.70");
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(42L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(OrderEventType.CREATE);
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rejectsCreationBeforeRouteCallWhenPassengerHasUnfinishedOrder() {
        Passenger passenger = new Passenger();
        passenger.setId(1L);
        User user = new User();
        user.setId(1L);
        user.setStatus("active");
        given(passengerMapper.lockById(1L)).willReturn(passenger);
        given(userMapper.selectById(1L)).willReturn(user);
        given(rideOrderMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.create(1L, request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(amapRouteClient, never()).estimateDrivingRoute(any(), any());
        verify(rideOrderMapper, never()).insert(any(RideOrder.class));
    }

    private OrderQuoteRequest request() {
        return new OrderQuoteRequest(
                new PlaceRequest("pickup", "天安门", "北京市东城区", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "外滩", "上海市黄浦区", new BigDecimal("31.230416"), new BigDecimal("121.473701")));
    }
}
