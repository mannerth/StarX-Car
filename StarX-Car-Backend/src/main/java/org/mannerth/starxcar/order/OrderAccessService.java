package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Centralizes resource existence and ownership checks for every order workflow. */
@Service
public class OrderAccessService {
    private final RideOrderMapper rideOrderMapper;

    public OrderAccessService(RideOrderMapper rideOrderMapper) {
        this.rideOrderMapper = rideOrderMapper;
    }

    public RideOrder requireByOrderNo(String orderNo) {
        List<RideOrder> orders = rideOrderMapper.selectList(Wrappers.<RideOrder>lambdaQuery()
                .eq(RideOrder::getOrderNo, orderNo));
        if (orders.size() != 1) {
            throw notFound();
        }
        return orders.getFirst();
    }

    public RideOrder requirePassengerOrder(String orderNo, Long passengerId) {
        RideOrder order = requireByOrderNo(orderNo);
        if (!passengerId.equals(order.getPassengerId())) {
            throw notFound();
        }
        return order;
    }

    public RideOrder requireDriverOrder(String orderNo, Long driverId) {
        RideOrder order = requireByOrderNo(orderNo);
        if (!driverId.equals(order.getDriverId())) {
            throw notFound();
        }
        return order;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
    }
}
