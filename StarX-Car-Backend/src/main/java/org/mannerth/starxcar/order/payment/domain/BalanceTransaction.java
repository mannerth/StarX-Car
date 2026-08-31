package org.mannerth.starxcar.order.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("balance_transaction")
public class BalanceTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionNo;
    private Long userId;
    private Long orderId;
    private Long complaintId;
    private BalanceTransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String idempotencyKey;
    private String description;
    private LocalDateTime createdTime;
}
