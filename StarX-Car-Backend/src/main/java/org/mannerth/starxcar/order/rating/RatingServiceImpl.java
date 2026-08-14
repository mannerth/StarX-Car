package org.mannerth.starxcar.order.rating;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.OrderAccessService;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.RideOrder;
import org.mannerth.starxcar.order.RideOrderMapper;
import org.mannerth.starxcar.order.rating.request.RatingSubmitRequest;
import org.mannerth.starxcar.order.rating.response.OrderRatingResponse;
import org.mannerth.starxcar.order.rating.response.RatingPageResponse;
import org.mannerth.starxcar.order.rating.response.RatingSummaryResponse;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RatingServiceImpl implements RatingService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration RATING_WINDOW = Duration.ofDays(7);

    private final OrderRatingMapper ratingMapper;
    private final RideOrderMapper rideOrderMapper;
    private final OrderAccessService orderAccessService;
    private final DriverMapper driverMapper;
    private final Clock clock;

    public RatingServiceImpl(OrderRatingMapper ratingMapper, RideOrderMapper rideOrderMapper,
                             OrderAccessService orderAccessService, DriverMapper driverMapper, Clock clock) {
        this.ratingMapper = ratingMapper;
        this.rideOrderMapper = rideOrderMapper;
        this.orderAccessService = orderAccessService;
        this.driverMapper = driverMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrderRatingResponse submit(Long passengerId, String orderNo, RatingSubmitRequest request) {
        RideOrder order = orderAccessService.requirePassengerOrder(orderNo, passengerId);
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单未完成，不能评价");
        }
        LocalDateTime completedTime = order.getCompletedTime();
        if (completedTime == null || Duration.between(completedTime, now()).compareTo(RATING_WINDOW) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已超过评价时限（订单完成后 7 天内）");
        }
        if (order.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单缺少司机，不能评价");
        }
        if (ratingMapper.selectCount(new LambdaQueryWrapper<OrderRating>()
                .eq(OrderRating::getOrderId, order.getId())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已评价，不能重复评价");
        }
        OrderRating rating = new OrderRating();
        rating.setOrderId(order.getId());
        rating.setPassengerId(passengerId);
        rating.setDriverId(order.getDriverId());
        rating.setRating(request.rating());
        rating.setContent(normalizeContent(request.content()));
        rating.setCreatedTime(now());
        if (ratingMapper.insert(rating) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存评价失败");
        }
        if (driverMapper.addRating(order.getDriverId(), request.rating()) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "更新司机评分失败");
        }
        return new OrderRatingResponse(true, rating.getRating(), rating.getContent(), rating.getCreatedTime());
    }

    @Override
    public OrderRatingResponse getOrderRating(Long requesterId, UserType requesterType, String orderNo) {
        RideOrder order = switch (requesterType) {
            case passenger -> orderAccessService.requirePassengerOrder(orderNo, requesterId);
            case driver -> orderAccessService.requireDriverOrder(orderNo, requesterId);
            case admin -> orderAccessService.requireByOrderNo(orderNo);
        };
        OrderRating rating = ratingMapper.selectOne(new LambdaQueryWrapper<OrderRating>()
                .eq(OrderRating::getOrderId, order.getId()));
        if (rating == null) {
            return new OrderRatingResponse(false, null, null, null);
        }
        return new OrderRatingResponse(true, rating.getRating(), rating.getContent(), rating.getCreatedTime());
    }

    @Override
    public RatingPageResponse listDriverRatings(Long driverId, long page, long size) {
        return listRatings(new LambdaQueryWrapper<OrderRating>()
                .eq(OrderRating::getDriverId, driverId)
                .orderByDesc(OrderRating::getCreatedTime)
                .orderByDesc(OrderRating::getId), page, size);
    }

    @Override
    public RatingPageResponse listPassengerRatings(Long passengerId, long page, long size) {
        return listRatings(new LambdaQueryWrapper<OrderRating>()
                .eq(OrderRating::getPassengerId, passengerId)
                .orderByDesc(OrderRating::getCreatedTime)
                .orderByDesc(OrderRating::getId), page, size);
    }

    private RatingPageResponse listRatings(LambdaQueryWrapper<OrderRating> query, long page, long size) {
        IPage<OrderRating> result = ratingMapper.selectPage(new Page<>(page, size), query);
        List<Long> orderIds = result.getRecords().stream().map(OrderRating::getOrderId).distinct().toList();
        Map<Long, RideOrder> orders = orderIds.isEmpty() ? Map.of()
                : rideOrderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(RideOrder::getId, Function.identity()));
        List<RatingSummaryResponse> records = result.getRecords().stream().map(rating -> {
            RideOrder order = orders.get(rating.getOrderId());
            return new RatingSummaryResponse(order == null ? null : order.getOrderNo(), rating.getRating(),
                    rating.getContent(), rating.getCreatedTime());
        }).toList();
        return new RatingPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }
}