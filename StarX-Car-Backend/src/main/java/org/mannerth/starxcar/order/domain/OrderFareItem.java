package org.mannerth.starxcar.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("order_fare_item")
public class OrderFareItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private FareChargeType chargeType;
    private Long ruleId;
    private Integer ruleVersion;
    private Long tierId;
    private Long rangeStart;
    private Long rangeEnd;
    private Long actualUsage;
    private Long unitSize;
    private BigDecimal unitPrice;
    private Long chargedUnits;
    private BigDecimal amount;
    private LocalDateTime createdTime;
}