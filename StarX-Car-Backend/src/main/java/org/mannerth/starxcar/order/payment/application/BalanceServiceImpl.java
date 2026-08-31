package org.mannerth.starxcar.order.payment.application;

import org.mannerth.starxcar.order.payment.domain.BalanceTransaction;
import org.mannerth.starxcar.order.payment.domain.BalanceTransactionType;
import org.mannerth.starxcar.order.payment.domain.UserBalanceAccount;
import org.mannerth.starxcar.order.payment.infrastructure.BalanceTransactionMapper;
import org.mannerth.starxcar.order.payment.infrastructure.UserBalanceAccountMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BalanceServiceImpl implements BalanceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserBalanceAccountMapper accountMapper;
    private final BalanceTransactionMapper transactionMapper;
    private final Clock clock;

    public BalanceServiceImpl(UserBalanceAccountMapper accountMapper, BalanceTransactionMapper transactionMapper,
                              Clock clock) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.clock = clock;
    }

    @Override
    public BigDecimal change(Long userId, Long orderId, Long complaintId, BigDecimal amount,
                             String transactionType, String idempotencyKey, String description,
                             boolean rejectNegativeBalance) {
        if (userId == null || amount == null || amount.signum() == 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("余额变更参数无效");
        }
        accountMapper.ensureAccount(userId);
        UserBalanceAccount account = accountMapper.selectByUserIdForUpdate(userId);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建余额账户失败");
        }
        BigDecimal before = account.getBalance();
        BigDecimal after = before.add(amount);
        if (rejectNegativeBalance && after.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "余额不足");
        }
        if (accountMapper.updateBalance(account.getId(), account.getVersion(), after) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "余额已变化");
        }
        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setTransactionNo("B" + UUID.randomUUID().toString().replace("-", ""));
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setComplaintId(complaintId);
        transaction.setTransactionType(BalanceTransactionType.valueOf(transactionType));
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setDescription(description);
        transaction.setCreatedTime(LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0));
        if (transactionMapper.insert(transaction) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录余额流水失败");
        }
        return after;
    }
}
