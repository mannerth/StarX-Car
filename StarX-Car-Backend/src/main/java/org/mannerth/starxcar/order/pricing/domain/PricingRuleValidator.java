package org.mannerth.starxcar.order.pricing.domain;

import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.RoundingMode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;






/** Validates the contiguous, non-overlapping piecewise functions used for charging. */
@Component
public class PricingRuleValidator {
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;
    private static final EnumSet<FareChargeType> TIER_CHARGE_TYPES = EnumSet.of(
            FareChargeType.DISTANCE, FareChargeType.DRIVING_TIME, FareChargeType.WAITING_TIME);

    public void validate(PricingRuleDefinition definition) {
        require(definition.id() != null && definition.id() > 0, "计价规则 ID 无效");
        require(definition.name() != null && !definition.name().isBlank(), "计价规则名称不能为空");
        require(definition.version() > 0, "计价规则版本必须大于 0");
        require(definition.status() != null, "计价规则状态不能为空");
        requireUnsigned(definition.includedDistanceM(), "起步价包含距离");
        requireMoney(definition.baseFare(), "起步价");
        requireMoney(definition.minimumFare(), "最低消费");
        require(definition.tiers() != null, "计价分段不能为空");

        for (PricingTierDefinition tier : definition.tiers()) {
            require(tier != null, "计价分段不能为空");
            require(TIER_CHARGE_TYPES.contains(tier.chargeType()), "不支持的分段收费类型");
        }
        for (FareChargeType chargeType : TIER_CHARGE_TYPES) {
            validateChargeType(chargeType, definition.tiers().stream()
                    .filter(tier -> tier.chargeType() == chargeType)
                    .sorted(Comparator.comparingInt(PricingTierDefinition::sortNo))
                    .toList());
        }
    }

    private void validateChargeType(FareChargeType chargeType, List<PricingTierDefinition> tiers) {
        require(!tiers.isEmpty(), chargeType + " 必须配置至少一个分段");
        long expectedStart = 0;
        boolean hasUnlimitedTier = false;
        int previousSortNo = -1;
        for (int index = 0; index < tiers.size(); index++) {
            PricingTierDefinition tier = tiers.get(index);
            require(tier.id() != null && tier.id() > 0, "计价分段 ID 无效");
            require(tier.sortNo() >= 0 && tier.sortNo() > previousSortNo, chargeType + " 分段顺序无效");
            require(tier.rangeStart() == expectedStart, chargeType + " 分段区间存在空洞或重叠");
            requireUnsigned(tier.rangeStart(), chargeType + " 分段起点");
            requireUnsigned(tier.unitSize(), chargeType + " 计费单位");
            require(tier.unitSize() > 0, chargeType + " 计费单位必须大于 0");
            requireMoney(tier.unitPrice(), chargeType + " 单位价格");
            require(tier.roundingMode() == RoundingMode.CEIL, chargeType + " 取整方式不受支持");

            if (tier.rangeEnd() == null) {
                require(index == tiers.size() - 1, chargeType + " 无上限分段必须位于最后");
                hasUnlimitedTier = true;
            } else {
                requireUnsigned(tier.rangeEnd(), chargeType + " 分段终点");
                require(tier.rangeEnd() > tier.rangeStart(), chargeType + " 分段终点必须大于起点");
                expectedStart = tier.rangeEnd();
            }
            previousSortNo = tier.sortNo();
        }
        require(hasUnlimitedTier, chargeType + " 必须以无上限分段结束");
    }

    private void requireUnsigned(long value, String fieldName) {
        require(value >= 0 && value <= MAX_UNSIGNED_INT, fieldName + " 超出无符号整数字段范围");
    }

    private void requireMoney(BigDecimal value, String fieldName) {
        require(value != null && value.signum() >= 0 && value.scale() <= 2, fieldName + " 必须是最多两位小数的非负金额");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}