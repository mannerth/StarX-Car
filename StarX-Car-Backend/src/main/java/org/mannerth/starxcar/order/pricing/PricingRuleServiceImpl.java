package org.mannerth.starxcar.order.pricing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.mannerth.starxcar.order.PricingRuleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class PricingRuleServiceImpl implements PricingRuleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PricingRuleServiceImpl.class);

    private final PricingRuleMapper pricingRuleMapper;
    private final PricingRuleTierMapper pricingRuleTierMapper;
    private final PricingRuleValidator validator;

    public PricingRuleServiceImpl(PricingRuleMapper pricingRuleMapper, PricingRuleTierMapper pricingRuleTierMapper,
                                  PricingRuleValidator validator) {
        this.pricingRuleMapper = pricingRuleMapper;
        this.pricingRuleTierMapper = pricingRuleTierMapper;
        this.validator = validator;
    }

    @Override
    public PricingRuleDefinition getDefaultActiveRule() {
        List<PricingRule> rules = pricingRuleMapper.selectList(Wrappers.<PricingRule>lambdaQuery()
                .eq(PricingRule::getStatus, PricingRuleStatus.active)
                .eq(PricingRule::getDefaultRule, true));
        if (rules.size() != 1) {
            LOGGER.error("Expected exactly one active default pricing rule but found count={}", rules.size());
            throw unavailable();
        }
        return loadAndValidate(rules.getFirst());
    }

    @Override
    public PricingRuleDefinition getByIdAndVersion(Long ruleId, int version) {
        if (ruleId == null || ruleId <= 0 || version <= 0) {
            throw unavailable();
        }
        List<PricingRule> rules = pricingRuleMapper.selectList(Wrappers.<PricingRule>lambdaQuery()
                .eq(PricingRule::getId, ruleId)
                .eq(PricingRule::getVersion, version));
        if (rules.size() != 1) {
            LOGGER.error("Expected exactly one pricing rule for ruleId={} version={} but found count={}",
                    ruleId, version, rules.size());
            throw unavailable();
        }
        return loadAndValidate(rules.getFirst());
    }

    private PricingRuleDefinition loadAndValidate(PricingRule rule) {
        List<PricingTierDefinition> tiers = pricingRuleTierMapper.selectList(Wrappers.<PricingRuleTier>lambdaQuery()
                        .eq(PricingRuleTier::getRuleId, rule.getId())
                        .orderByAsc(PricingRuleTier::getChargeType)
                        .orderByAsc(PricingRuleTier::getSortNo))
                .stream()
                .map(this::toDefinition)
                .sorted(Comparator.comparing(PricingTierDefinition::chargeType)
                        .thenComparingInt(PricingTierDefinition::sortNo))
                .toList();
        PricingRuleDefinition definition = new PricingRuleDefinition(rule.getId(), rule.getName(), rule.getVersion(),
                rule.getStatus(), Boolean.TRUE.equals(rule.getDefaultRule()), rule.getBaseFare(),
                rule.getIncludedDistanceM(), rule.getMinimumFare(), tiers);
        try {
            validator.validate(definition);
            return definition;
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Pricing rule configuration is invalid: ruleId={} version={} reason={}",
                    rule.getId(), rule.getVersion(), exception.getMessage());
            throw unavailable();
        }
    }

    private PricingTierDefinition toDefinition(PricingRuleTier tier) {
        return new PricingTierDefinition(tier.getId(), tier.getChargeType(), tier.getRangeStart(), tier.getRangeEnd(),
                tier.getUnitSize(), tier.getUnitPrice(), tier.getRoundingMode(), tier.getSortNo());
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "计价规则配置不可用");
    }
}
