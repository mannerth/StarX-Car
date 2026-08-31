package org.mannerth.starxcar.order.payment.application;

import java.math.BigDecimal;

/** Records one immutable balance mutation inside the caller's transaction. */
public interface BalanceService {
    BigDecimal change(Long userId, Long orderId, Long complaintId, BigDecimal amount,
                      String transactionType, String idempotencyKey, String description,
                      boolean rejectNegativeBalance);
}
