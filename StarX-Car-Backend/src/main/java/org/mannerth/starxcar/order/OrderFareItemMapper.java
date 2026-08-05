package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderFareItemMapper extends BaseMapper<OrderFareItem> {

    @Insert("""
            <script>
            INSERT INTO order_fare_item
                (order_id, charge_type, rule_id, rule_version, tier_id, range_start, range_end, actual_usage,
                 unit_size, unit_price, charged_units, amount, created_time)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.orderId}, #{item.chargeType}, #{item.ruleId}, #{item.ruleVersion}, #{item.tierId},
                 #{item.rangeStart}, #{item.rangeEnd}, #{item.actualUsage}, #{item.unitSize}, #{item.unitPrice},
                 #{item.chargedUnits}, #{item.amount}, #{item.createdTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<OrderFareItem> items);
}
