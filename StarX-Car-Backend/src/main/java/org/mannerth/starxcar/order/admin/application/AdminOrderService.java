package org.mannerth.starxcar.order.admin.application;

import org.mannerth.starxcar.order.admin.response.AdminDriverLocationResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderFareItemResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderNoteResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderPageResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderSummaryMetricsResponse;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
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