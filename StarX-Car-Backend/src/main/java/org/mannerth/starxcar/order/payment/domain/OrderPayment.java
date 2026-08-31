package org.mannerth.starxcar.order.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_payment")
public class OrderPayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long payerUserId;
    private Long payeeUserId;
    private BigDecimal amountDue;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private BigDecimal driverIncomeAmount;
    private String status;
    private LocalDateTime paidTime;
    private LocalDateTime completedTime;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
