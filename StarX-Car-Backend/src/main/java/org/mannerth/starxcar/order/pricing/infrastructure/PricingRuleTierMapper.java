package org.mannerth.starxcar.order.pricing.infrastructure;

import org.mannerth.starxcar.order.pricing.domain.PricingRuleTier;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;






@Mapper
public interface PricingRuleTierMapper extends BaseMapper<PricingRuleTier> {

    @Delete("DELETE FROM pricing_rule_tier WHERE rule_id = #{ruleId}")
    int deleteByRuleId(@Param("ruleId") Long ruleId);

    @Insert("""
            <script>
            INSERT INTO pricing_rule_tier
                (rule_id, charge_type, range_start, range_end, unit_size, unit_price, rounding_mode, sort_no)
            VALUES
            <foreach collection='tiers' item='tier' separator=','>
                (#{tier.ruleId}, #{tier.chargeType}, #{tier.rangeStart}, #{tier.rangeEnd}, #{tier.unitSize},
                 #{tier.unitPrice}, #{tier.roundingMode}, #{tier.sortNo})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("tiers") List<PricingRuleTier> tiers);
}