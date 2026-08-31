package org.mannerth.starxcar.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_cancellation")
public class OrderCancellation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private OrderStatus terminalStatus;
    private OrderOperatorType initiatorType;
    private OrderStatus cancellationStage;
    private String reasonCode;
    private String reasonDetail;
    private BigDecimal chargedAmount;
    private BigDecimal actualEndLat;
    private BigDecimal actualEndLng;
    private LocalDateTime endedTime;
    private LocalDateTime createdTime;
}
