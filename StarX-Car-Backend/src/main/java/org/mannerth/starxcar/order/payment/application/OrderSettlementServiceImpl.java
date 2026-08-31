package org.mannerth.starxcar.order.payment.application;

import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.payment.domain.OrderPayment;
import org.mannerth.starxcar.order.payment.infrastructure.OrderPaymentMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderSettlementServiceImpl implements OrderSettlementService {
    private final BalanceService balanceService;
    private final OrderPaymentMapper paymentMapper;

    public OrderSettlementServiceImpl(BalanceService balanceService, OrderPaymentMapper paymentMapper) {
        this.balanceService = balanceService;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public void settle(RideOrder order, BigDecimal amount, LocalDateTime paidTime) {
        if (order.getDriverId() == null || amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单缺少结算所需信息");
        }
        String paymentNo = "P" + UUID.randomUUID().toString().replace("-", "");
        balanceService.change(order.getPassengerId(), order.getId(), null, amount.negate(), "ORDER_PAYMENT",
                paymentNo + ":payer", "订单余额扣款", false);
        balanceService.change(order.getDriverId(), order.getId(), null, amount, "DRIVER_INCOME",
                paymentNo + ":driver", "订单收入", false);
        OrderPayment payment = new OrderPayment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(order.getId());
        payment.setPayerUserId(order.getPassengerId());
        payment.setPayeeUserId(order.getDriverId());
        payment.setAmountDue(amount);
        payment.setPaidAmount(amount);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setDriverIncomeAmount(amount);
        payment.setStatus("PAID");
        payment.setPaidTime(paidTime);
        if (paymentMapper.insert(payment) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建订单支付记录失败");
        }
    }
}
