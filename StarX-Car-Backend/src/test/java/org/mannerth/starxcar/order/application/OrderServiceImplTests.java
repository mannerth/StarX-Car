package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.infrastructure.amap.AmapRouteClient;
import org.mannerth.starxcar.infrastructure.amap.RouteEstimate;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.pricing.application.PricingRuleService;
import org.mannerth.starxcar.order.pricing.domain.FareCalculator;
import org.mannerth.starxcar.order.pricing.domain.PricingFixtures;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleValidator;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.request.PlaceRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
                new OrderNumberGenerator(clock), new org.mannerth.starxcar.config.ReservationProperties(), clock);
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

    @Test
    void reservationQuoteValidatesWindowAndEchoesTypeAndTime() {
        given(pricingRuleService.getDefaultActiveRule()).willReturn(PricingFixtures.defaultRule());
        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));

        OrderQuoteResponse quote = service.quote(reservationRequest(validReservationTime()));

        assertThat(quote.orderType()).isEqualTo(OrderType.SCHEDULED);
        assertThat(quote.scheduledPickupTime()).isEqualTo(validReservationTime());
        assertThat(quote.fare().totalFare()).isEqualByComparingTo("42.70");
    }

    @Test
    void reservationQuoteRejectsMissingTooEarlyAndTooFarTimes() {
        given(pricingRuleService.getDefaultActiveRule()).willReturn(PricingFixtures.defaultRule());
        given(amapRouteClient.estimateDrivingRoute(any(), any())).willReturn(new RouteEstimate(12_001, 1_801));

        assertThatThrownBy(() -> service.quote(reservationRequest(null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.quote(reservationRequest(LocalDateTime.of(2026, 8, 4, 20, 15))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.quote(reservationRequest(LocalDateTime.of(2026, 8, 15, 20, 0))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(amapRouteClient, never()).estimateDrivingRoute(any(), any());
    }

    @Test
    void instantQuoteRejectsScheduledPickupTime() {
        assertThatThrownBy(() -> service.quote(new OrderQuoteRequest(
                new PlaceRequest("pickup", "天安门", "北京市东城区", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "外滩", "上海市黄浦区", new BigDecimal("31.230416"), new BigDecimal("121.473701")),
                OrderType.INSTANT, validReservationTime())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createsReservationAsScheduledWithDispatchTimeNull() {
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
            invocation.getArgument(0, RideOrder.class).setId(43L);
            return 1;
        });
        given(rideOrderEventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderCreatedResponse created = service.create(1L, reservationRequest(validReservationTime()));

        assertThat(created.status()).isEqualTo(OrderStatus.SCHEDULED);
        org.mockito.ArgumentCaptor<RideOrder> orderCaptor = org.mockito.ArgumentCaptor.forClass(RideOrder.class);
        org.mockito.ArgumentCaptor<RideOrderEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(RideOrderEvent.class);
        verify(rideOrderMapper).insert(orderCaptor.capture());
        verify(rideOrderEventMapper).insert(eventCaptor.capture());
        RideOrder saved = orderCaptor.getValue();
        assertThat(saved.getOrderType()).isEqualTo(OrderType.SCHEDULED);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(saved.getScheduledPickupTime()).isEqualTo(validReservationTime());
        assertThat(saved.getDispatchTime()).isNull();
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(OrderEventType.CREATE);
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.SCHEDULED);
    }

    @Test
    void rejectsSecondReservationWhileOneIsActive() {
        Passenger passenger = new Passenger();
        passenger.setId(1L);
        User user = new User();
        user.setId(1L);
        user.setStatus("active");
        given(passengerMapper.lockById(1L)).willReturn(passenger);
        given(userMapper.selectById(1L)).willReturn(user);
        given(rideOrderMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.create(1L, reservationRequest(validReservationTime())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(amapRouteClient, never()).estimateDrivingRoute(any(), any());
        verify(rideOrderMapper, never()).insert(any(RideOrder.class));
    }

    @Test
    void reservationCreationCoexistsWithInProgressInstantTrip() {
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
            invocation.getArgument(0, RideOrder.class).setId(44L);
            return 1;
        });
        given(rideOrderEventMapper.insert(any(RideOrderEvent.class))).willReturn(1);

        OrderCreatedResponse created = service.create(1L, reservationRequest(validReservationTime()));

        assertThat(created.status()).isEqualTo(OrderStatus.SCHEDULED);
        verify(rideOrderMapper).insert(any(RideOrder.class));
    }

    private OrderQuoteRequest reservationRequest(LocalDateTime scheduledPickupTime) {
        return new OrderQuoteRequest(
                new PlaceRequest("pickup", "天安门", "北京市东城区", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "外滩", "上海市黄浦区", new BigDecimal("31.230416"), new BigDecimal("121.473701")),
                OrderType.SCHEDULED, scheduledPickupTime);
    }

    private LocalDateTime validReservationTime() {
        return LocalDateTime.of(2026, 8, 4, 21, 0);
    }

    private OrderQuoteRequest request() {
        return new OrderQuoteRequest(
                new PlaceRequest("pickup", "天安门", "北京市东城区", new BigDecimal("39.916527"), new BigDecimal("116.397128")),
                new PlaceRequest("destination", "外滩", "上海市黄浦区", new BigDecimal("31.230416"), new BigDecimal("121.473701")));
    }
}
