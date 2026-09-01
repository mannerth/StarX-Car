package org.mannerth.starxcar.order.pricing.infrastructure;

import org.mannerth.starxcar.order.pricing.domain.PricingRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;






@Mapper
public interface PricingRuleMapper extends BaseMapper<PricingRule> {

    @Select("SELECT version FROM pricing_rule WHERE name = #{name} ORDER BY version DESC LIMIT 1 FOR UPDATE")
    Integer selectLatestVersionForUpdate(@Param("name") String name);

    @Select("SELECT * FROM pricing_rule WHERE id = #{ruleId} FOR UPDATE")
    PricingRule selectByIdForUpdate(@Param("ruleId") Long ruleId);

    @Select("SELECT id FROM pricing_rule WHERE status = 'active' AND is_default = 1 FOR UPDATE")
    List<Long> lockActiveDefaultRuleIds();

    @Update("UPDATE pricing_rule SET status = 'inactive', is_default = 0, updated_by = #{updatedBy} "
            + "WHERE status = 'active' AND is_default = 1 AND id <> #{exceptRuleId}")
    int deactivateOtherDefaultRules(@Param("exceptRuleId") Long exceptRuleId, @Param("updatedBy") Long updatedBy);

    @Update("UPDATE pricing_rule SET status = 'active', is_default = #{defaultRule}, updated_by = #{updatedBy} "
            + "WHERE id = #{ruleId} AND status = 'draft'")
    int activateDraftRule(@Param("ruleId") Long ruleId, @Param("updatedBy") Long updatedBy,
                           @Param("defaultRule") boolean defaultRule);

    @Update("UPDATE pricing_rule SET is_default = 1, updated_by = #{updatedBy} "
            + "WHERE id = #{ruleId} AND status = 'active' AND is_default = 0")
    int makeActiveRuleDefault(@Param("ruleId") Long ruleId, @Param("updatedBy") Long updatedBy);

    @Update("UPDATE pricing_rule SET status = 'archived', is_default = 0, updated_by = #{updatedBy} "
            + "WHERE id = #{ruleId} AND is_default = 0 AND status <> 'archived'")
    int archiveNonDefaultRule(@Param("ruleId") Long ruleId, @Param("updatedBy") Long updatedBy);
}
