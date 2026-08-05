package org.mannerth.starxcar.order;

import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;

public interface OrderService {
    OrderQuoteResponse quote(OrderQuoteRequest request);

    OrderCreatedResponse create(Long passengerId, OrderQuoteRequest request);
}
