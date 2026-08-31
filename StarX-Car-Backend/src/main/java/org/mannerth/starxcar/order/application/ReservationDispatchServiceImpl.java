package org.mannerth.starxcar.order.application;

import org.mannerth.starxcar.config.ReservationProperties;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;






@Service
public class ReservationDispatchServiceImpl implements ReservationDispatchService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RideOrderMapper orderMapper;
    private final RideOrderEventMapper eventMapper;
    private final ReservationProperties reservationProperties;
    private final Clock clock;

    public ReservationDispatchServiceImpl(RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                          ReservationProperties reservationProperties, Clock clock) {
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.reservationProperties = reservationProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int dispatchDueReservations() {
        LocalDateTime dispatchDeadline = now().plusMinutes(reservationProperties.getDispatchAheadMinutes());
        List<RideOrder> due = orderMapper.selectList(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getOrderType, OrderType.SCHEDULED)
                .eq(RideOrder::getStatus, OrderStatus.SCHEDULED)
                .le(RideOrder::getScheduledPickupTime, dispatchDeadline));
        int dispatched = 0;
        for (RideOrder order : due) {
            LocalDateTime dispatchTime = now();
            if (orderMapper.dispatchReservation(order.getId(), order.getVersion(), dispatchTime) != 1) {
                continue;
            }
            writeEvent(order.getId(), OrderEventType.DISPATCH, OrderStatus.SCHEDULED, OrderStatus.PENDING,
                    dispatchTime, null);
            dispatched++;
        }
        return dispatched;
    }

    @Override
    @Transactional
    public int expireStaleReservations() {
        LocalDateTime expireBefore = now().minusMinutes(reservationProperties.getExpireGraceMinutes());
        List<RideOrder> stale = orderMapper.selectList(new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getOrderType, OrderType.SCHEDULED)
                .eq(RideOrder::getStatus, OrderStatus.PENDING)
                .lt(RideOrder::getScheduledPickupTime, expireBefore));
        int cancelled = 0;
        for (RideOrder order : stale) {
            LocalDateTime cancelledTime = now();
            if (orderMapper.cancelOrder(order.getId(), OrderStatus.PENDING, order.getVersion(), cancelledTime,
                    OrderOperatorType.system.name(), staleCancelReason()) != 1) {
                continue;
            }
            writeEvent(order.getId(), OrderEventType.CANCEL, OrderStatus.PENDING, OrderStatus.CANCELLED,
                    cancelledTime, staleCancelReason());
            cancelled++;
        }
        return cancelled;
    }

    private void writeEvent(Long orderId, OrderEventType eventType, OrderStatus fromStatus, OrderStatus toStatus,
                            LocalDateTime time, String remark) {
        RideOrderEvent event = new RideOrderEvent();
        event.setOrderId(orderId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorType(OrderOperatorType.system);
        event.setRemark(remark);
        event.setCreatedTime(time);
        if (eventMapper.insert(event) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录订单事件失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }

    private String staleCancelReason() {
        return "超过预约时间" + reservationProperties.getExpireGraceMinutes() + "分钟无司机接单";
    }
}