package org.mannerth.starxcar.order.payment.application;

import org.mannerth.starxcar.order.domain.RideOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderSettlementService {
    void settle(RideOrder order, BigDecimal amount, LocalDateTime paidTime);
}
