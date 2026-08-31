package org.mannerth.starxcar.order.rating.application;

import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.order.application.OrderAccessService;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.rating.domain.OrderRating;
import org.mannerth.starxcar.order.rating.infrastructure.OrderRatingMapper;
import org.mannerth.starxcar.order.rating.request.RatingSubmitRequest;
import org.mannerth.starxcar.order.rating.response.OrderRatingResponse;
import org.mannerth.starxcar.order.rating.response.RatingPageResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;




class RatingServiceImplTests {
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-13T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));

    private OrderRatingMapper ratingMapper;
    private RideOrderMapper rideOrderMapper;
    private OrderAccessService accessService;
    private DriverMapper driverMapper;
    private RatingService service;

    @BeforeEach
    void setUp() {
        ratingMapper = mock(OrderRatingMapper.class);
        rideOrderMapper = mock(RideOrderMapper.class);
        accessService = mock(OrderAccessService.class);
        driverMapper = mock(DriverMapper.class);
        service = new RatingServiceImpl(ratingMapper, rideOrderMapper, accessService, driverMapper,
                Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void submitInsertsRatingAndUpdatesDriverAggregate() {
        RideOrder order = completedOrder();
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(ratingMapper.selectCount(any())).willReturn(0L);
        given(ratingMapper.insert(any(OrderRating.class))).willReturn(1);
        given(driverMapper.addRating(2L, 5)).willReturn(1);

        OrderRatingResponse result = service.submit(1L, "O1", new RatingSubmitRequest(5, " 司机服务很好 "));

        assertThat(result.rated()).isTrue();
        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.content()).isEqualTo("司机服务很好");
        assertThat(result.createdAt()).isEqualTo(NOW);
        verify(driverMapper).addRating(2L, 5);
    }

    @Test
    void submitRejectsOrderThatIsNotCompleted() {
        RideOrder order = completedOrder();
        order.setStatus(OrderStatus.IN_SERVICE);
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);

        assertConflict(() -> service.submit(1L, "O1", new RatingSubmitRequest(5, null)));
        verify(ratingMapper, never()).insert(any(OrderRating.class));
        verify(driverMapper, never()).addRating(anyLong(), anyInt());
    }

    @Test
    void submitRejectsOrderPastTheSevenDayWindow() {
        RideOrder order = completedOrder();
        order.setCompletedTime(NOW.minusDays(8));
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);

        assertConflict(() -> service.submit(1L, "O1", new RatingSubmitRequest(5, null)));
        verify(ratingMapper, never()).insert(any(OrderRating.class));
    }

    @Test
    void submitRejectsDuplicateRatingForSameOrder() {
        RideOrder order = completedOrder();
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(ratingMapper.selectCount(any())).willReturn(1L);

        assertConflict(() -> service.submit(1L, "O1", new RatingSubmitRequest(5, null)));
        verify(ratingMapper, never()).insert(any(OrderRating.class));
        verify(driverMapper, never()).addRating(anyLong(), anyInt());
    }

    @Test
    void submitInsertFailureDoesNotUpdateDriverAggregate() {
        RideOrder order = completedOrder();
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(ratingMapper.selectCount(any())).willReturn(0L);
        given(ratingMapper.insert(any(OrderRating.class))).willReturn(0);

        assertThatThrownBy(() -> service.submit(1L, "O1", new RatingSubmitRequest(5, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
        verify(driverMapper, never()).addRating(anyLong(), anyInt());
    }

    @Test
    void getOrderRatingReturnsRatedStateForPassenger() {
        RideOrder order = completedOrder();
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        OrderRating rating = new OrderRating();
        rating.setOrderId(10L);
        rating.setRating(4);
        rating.setContent("不错");
        rating.setCreatedTime(NOW);
        given(ratingMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(rating);

        OrderRatingResponse result = service.getOrderRating(1L, UserType.passenger, "O1");

        assertThat(result.rated()).isTrue();
        assertThat(result.rating()).isEqualTo(4);
        assertThat(result.content()).isEqualTo("不错");
    }

    @Test
    void getOrderRatingReturnsUnratedStateWhenNoRatingExists() {
        RideOrder order = completedOrder();
        given(accessService.requirePassengerOrder("O1", 1L)).willReturn(order);
        given(ratingMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

        OrderRatingResponse result = service.getOrderRating(1L, UserType.passenger, "O1");

        assertThat(result.rated()).isFalse();
        assertThat(result.rating()).isNull();
    }

    @Test
    void listDriverRatingsJoinsOrderNumbersAndPaginates() {
        Page<OrderRating> pageResult = new Page<>(1, 20, 1);
        OrderRating rating = new OrderRating();
        rating.setOrderId(10L);
        rating.setDriverId(2L);
        rating.setRating(5);
        rating.setContent("很好");
        rating.setCreatedTime(NOW);
        pageResult.setRecords(List.of(rating));
        given(ratingMapper.selectPage(any(), any())).willReturn(pageResult);
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O20260813001");
        given(rideOrderMapper.selectBatchIds(List.of(10L))).willReturn(List.of(order));

        RatingPageResponse result = service.listDriverRatings(2L, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst().orderNo()).isEqualTo("O20260813001");
        assertThat(result.records().getFirst().rating()).isEqualTo(5);
    }

    private RideOrder completedOrder() {
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O1");
        order.setPassengerId(1L);
        order.setDriverId(2L);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedTime(NOW.minusDays(1));
        return order;
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}