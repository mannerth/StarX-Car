package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.RoundingMode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;






@Data
@TableName("pricing_rule_tier")
public class PricingRuleTier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private FareChargeType chargeType;
    private Long rangeStart;
    private Long rangeEnd;
    private Long unitSize;
    private BigDecimal unitPrice;
    private RoundingMode roundingMode;
    private Integer sortNo;
}