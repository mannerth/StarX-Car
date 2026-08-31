package org.mannerth.starxcar.order.payment.application;

import org.mannerth.starxcar.order.payment.domain.UserBalanceAccount;
import org.mannerth.starxcar.order.payment.infrastructure.UserBalanceAccountMapper;
import org.mannerth.starxcar.order.payment.response.BalanceResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletServiceImpl implements WalletService {
    private final UserBalanceAccountMapper accountMapper;
    private final BalanceService balanceService;

    public WalletServiceImpl(UserBalanceAccountMapper accountMapper, BalanceService balanceService) {
        this.accountMapper = accountMapper;
        this.balanceService = balanceService;
    }

    @Override
    @Transactional
    public BalanceResponse balance(Long userId) {
        accountMapper.ensureAccount(userId);
        UserBalanceAccount account = accountMapper.selectByUserIdForUpdate(userId);
        return new BalanceResponse(account.getBalance());
    }

    @Override
    @Transactional
    public BalanceResponse recharge(Long passengerId, BigDecimal amount) {
        return new BalanceResponse(balanceService.change(passengerId, null, null, amount, "RECHARGE",
                "recharge:" + UUID.randomUUID(), "模拟充值", false));
    }

    @Override
    @Transactional
    public BalanceResponse withdraw(Long driverId, BigDecimal amount) {
        return new BalanceResponse(balanceService.change(driverId, null, null, amount.negate(), "WITHDRAWAL",
                "withdraw:" + UUID.randomUUID(), "模拟提现", true));
    }
}
