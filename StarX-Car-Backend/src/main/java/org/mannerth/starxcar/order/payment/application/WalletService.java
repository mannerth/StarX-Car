package org.mannerth.starxcar.order.payment.application;

import org.mannerth.starxcar.order.payment.response.BalanceResponse;
import java.math.BigDecimal;

public interface WalletService {
    BalanceResponse balance(Long userId);
    BalanceResponse recharge(Long passengerId, BigDecimal amount);
    BalanceResponse withdraw(Long driverId, BigDecimal amount);
}
