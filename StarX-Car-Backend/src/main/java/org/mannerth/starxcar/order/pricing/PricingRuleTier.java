package org.mannerth.starxcar.order.pricing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.mannerth.starxcar.order.FareChargeType;
import org.mannerth.starxcar.order.RoundingMode;

import java.math.BigDecimal;

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
