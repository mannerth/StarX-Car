package org.mannerth.starxcar.order;

import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;

public interface OrderQueryService {
    OrderPageResponse listPassengerOrders(Long passengerId, long page, long size, OrderStatus status);

    OrderPageResponse listDriverOrders(Long driverId, long page, long size, OrderStatus status);

    OrderDetailResponse getOrder(Long requesterId, UserType requesterType, String orderNo);
}
