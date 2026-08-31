package org.mannerth.starxcar.order.admin;

import org.mannerth.starxcar.order.admin.response.*;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.OrderType;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminOrderService {
    AdminOrderPageResponse list(long page, long size, String orderNo, OrderType orderType, OrderStatus status,
                                String keyword, LocalDateTime createdFrom, LocalDateTime createdTo,
                                LocalDateTime scheduledFrom, LocalDateTime scheduledTo);

    AdminOrderSummaryMetricsResponse summary(LocalDateTime from, LocalDateTime to);

    OrderDetailResponse detail(Long adminId, String orderNo);

    List<AdminOrderFareItemResponse> fareItems(String orderNo);

    List<AdminOrderNoteResponse> notes(String orderNo);

    AdminDriverLocationResponse driverLocation(String orderNo);

    OrderStatusTransitionResponse cancel(Long adminId, String orderNo, String reason);

    OrderStatusTransitionResponse dispatchReservation(Long adminId, String orderNo, String reason);

    AdminOrderNoteResponse addNote(Long adminId, String orderNo, String content);
}
