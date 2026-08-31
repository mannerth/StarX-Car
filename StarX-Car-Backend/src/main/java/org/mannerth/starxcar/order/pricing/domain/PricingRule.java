package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;






@Data
@TableName("pricing_rule")
public class PricingRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer version;
    private PricingRuleStatus status;
    @TableField("is_default")
    private Boolean defaultRule;
    private BigDecimal baseFare;
    private Long includedDistanceM;
    private BigDecimal minimumFare;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}