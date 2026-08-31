package org.mannerth.starxcar.order.pricing.application;

import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.pricing.domain.PricingRule;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleDefinition;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleTier;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleValidator;
import org.mannerth.starxcar.order.pricing.domain.PricingTierDefinition;
import org.mannerth.starxcar.order.pricing.infrastructure.PricingRuleMapper;
import org.mannerth.starxcar.order.pricing.infrastructure.PricingRuleTierMapper;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.request.PricingTierWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRulePageResponse;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;
import org.mannerth.starxcar.order.pricing.response.PricingTierResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;






@Service
public class AdminPricingRuleServiceImpl implements AdminPricingRuleService {
    private final PricingRuleMapper ruleMapper;
    private final PricingRuleTierMapper tierMapper;
    private final RideOrderMapper orderMapper;
    private final PricingRuleValidator validator;

    public AdminPricingRuleServiceImpl(PricingRuleMapper ruleMapper, PricingRuleTierMapper tierMapper,
                                       RideOrderMapper orderMapper, PricingRuleValidator validator) {
        this.ruleMapper = ruleMapper;
        this.tierMapper = tierMapper;
        this.orderMapper = orderMapper;
        this.validator = validator;
    }

    @Override
    public PricingRulePageResponse list(long page, long size, PricingRuleStatus status) {
        Page<PricingRule> result = ruleMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<PricingRule>()
                .eq(status != null, PricingRule::getStatus, status)
                .orderByDesc(PricingRule::getCreatedTime)
                .orderByDesc(PricingRule::getId));
        return new PricingRulePageResponse(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::response).toList());
    }

    @Override
    @Transactional
    public PricingRuleResponse create(Long adminId, PricingRuleWriteRequest request) {
        String name = normalizedName(request.name());
        validateRequest(request, name);
        Integer latestVersion = ruleMapper.selectLatestVersionForUpdate(name);
        PricingRule rule = new PricingRule();
        rule.setName(name);
        if (latestVersion != null && latestVersion == Integer.MAX_VALUE) {
            throw conflict("同名计价规则版本号已达上限");
        }
        rule.setVersion(latestVersion == null ? 1 : latestVersion + 1);
        rule.setStatus(PricingRuleStatus.draft);
        rule.setDefaultRule(false);
        copyRuleFields(rule, request);
        rule.setCreatedBy(adminId);
        rule.setUpdatedBy(adminId);
        if (ruleMapper.insert(rule) != 1 || rule.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建计价规则失败");
        }
        List<PricingRuleTier> tiers = tiers(rule.getId(), request.tiers());
        insertTiers(tiers);
        return response(rule, tiers);
    }

    @Override
    public PricingRuleResponse get(Long ruleId) {
        PricingRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw notFound();
        }
        return response(rule);
    }

    @Override
    @Transactional
    public PricingRuleResponse updateDraft(Long adminId, Long ruleId, PricingRuleWriteRequest request) {
        String name = normalizedName(request.name());
        validateRequest(request, name);
        PricingRule rule = requireForUpdate(ruleId);
        if (rule.getStatus() != PricingRuleStatus.draft) {
            throw conflict("仅草稿规则可编辑");
        }
        if (!name.equals(rule.getName())) {
            throw conflict("草稿规则名称不可修改；请新建同名规则的新版本");
        }
        if (orderMapper.exists(new LambdaQueryWrapper<RideOrder>().eq(RideOrder::getPricingRuleId, ruleId))) {
            throw conflict("已被订单引用的规则不可编辑");
        }
        copyRuleFields(rule, request);
        rule.setUpdatedBy(adminId);
        if (ruleMapper.updateById(rule) != 1) {
            throw conflict("计价规则已变化");
        }
        tierMapper.deleteByRuleId(ruleId);
        List<PricingRuleTier> tiers = tiers(ruleId, request.tiers());
        insertTiers(tiers);
        return response(rule, tiers);
    }

    @Override
    @Transactional
    public PricingRuleResponse activate(Long adminId, Long ruleId, PricingRuleActivationRequest request) {
        PricingRule rule = requireForUpdate(ruleId);
        if (rule.getStatus() != PricingRuleStatus.draft) {
            throw conflict("仅草稿规则可激活");
        }
        validatePersistedRule(rule, tiersFor(ruleId));
        boolean setAsDefault = request != null && request.shouldSetAsDefault();
        if (setAsDefault) {
            ruleMapper.lockActiveDefaultRuleIds();
            ruleMapper.deactivateOtherDefaultRules(ruleId, adminId);
        }
        if (ruleMapper.activateDraftRule(ruleId, adminId, setAsDefault) != 1) {
            throw conflict("计价规则状态已变化");
        }
        rule.setStatus(PricingRuleStatus.active);
        rule.setDefaultRule(setAsDefault);
        rule.setUpdatedBy(adminId);
        return response(rule);
    }

    @Override
    @Transactional
    public PricingRuleResponse archive(Long adminId, Long ruleId) {
        PricingRule rule = requireForUpdate(ruleId);
        if (Boolean.TRUE.equals(rule.getDefaultRule()) && rule.getStatus() == PricingRuleStatus.active) {
            throw conflict("当前默认规则不可归档");
        }
        if (ruleMapper.archiveNonDefaultRule(ruleId, adminId) != 1) {
            throw conflict("计价规则当前不可归档");
        }
        rule.setStatus(PricingRuleStatus.archived);
        rule.setDefaultRule(false);
        rule.setUpdatedBy(adminId);
        return response(rule);
    }

    private PricingRule requireForUpdate(Long ruleId) {
        PricingRule rule = ruleMapper.selectByIdForUpdate(ruleId);
        if (rule == null) {
            throw notFound();
        }
        return rule;
    }

    private PricingRuleResponse response(PricingRule rule) {
        return response(rule, tiersFor(rule.getId()));
    }

    private PricingRuleResponse response(PricingRule rule, List<PricingRuleTier> tiers) {
        return new PricingRuleResponse(rule.getId(), rule.getName(), rule.getVersion(), rule.getStatus(),
                Boolean.TRUE.equals(rule.getDefaultRule()), rule.getBaseFare(), rule.getIncludedDistanceM(),
                rule.getMinimumFare(), rule.getCreatedBy(), rule.getUpdatedBy(), rule.getCreatedTime(),
                rule.getUpdatedTime(), tiers.stream().map(tier -> new PricingTierResponse(tier.getId(),
                        tier.getChargeType(), tier.getRangeStart(), tier.getRangeEnd(), tier.getUnitSize(),
                        tier.getUnitPrice(), tier.getRoundingMode(), tier.getSortNo())).toList());
    }

    private List<PricingRuleTier> tiersFor(Long ruleId) {
        return tierMapper.selectList(new LambdaQueryWrapper<PricingRuleTier>()
                        .eq(PricingRuleTier::getRuleId, ruleId)
                        .orderByAsc(PricingRuleTier::getChargeType)
                        .orderByAsc(PricingRuleTier::getSortNo))
                .stream()
                .sorted(Comparator.comparing(PricingRuleTier::getChargeType)
                        .thenComparing(PricingRuleTier::getSortNo))
                .toList();
    }

    private List<PricingRuleTier> tiers(Long ruleId, List<PricingTierWriteRequest> requests) {
        return requests.stream().map(request -> {
            PricingRuleTier tier = new PricingRuleTier();
            tier.setRuleId(ruleId);
            tier.setChargeType(request.chargeType());
            tier.setRangeStart(request.rangeStart());
            tier.setRangeEnd(request.rangeEnd());
            tier.setUnitSize(request.unitSize());
            tier.setUnitPrice(request.unitPrice());
            tier.setRoundingMode(request.roundingMode());
            tier.setSortNo(request.sortNo());
            return tier;
        }).toList();
    }

    private void insertTiers(List<PricingRuleTier> tiers) {
        if (tierMapper.insertBatch(tiers) != tiers.size()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入计价规则分段失败");
        }
    }

    private void validateRequest(PricingRuleWriteRequest request, String name) {
        PricingRuleDefinition definition = new PricingRuleDefinition(1L, name, 1, PricingRuleStatus.draft, false,
                request.baseFare(), request.includedDistanceM(), request.minimumFare(), request.tiers().stream()
                .map(tier -> new PricingTierDefinition(1L, tier.chargeType(), tier.rangeStart(), tier.rangeEnd(),
                        tier.unitSize(), tier.unitPrice(), tier.roundingMode(), tier.sortNo()))
                .toList());
        validateDefinition(definition);
    }

    private void validatePersistedRule(PricingRule rule, List<PricingRuleTier> tiers) {
        PricingRuleDefinition definition = new PricingRuleDefinition(rule.getId(), rule.getName(), rule.getVersion(),
                rule.getStatus(), Boolean.TRUE.equals(rule.getDefaultRule()), rule.getBaseFare(),
                rule.getIncludedDistanceM(), rule.getMinimumFare(), tiers.stream()
                .map(tier -> new PricingTierDefinition(tier.getId(), tier.getChargeType(), tier.getRangeStart(),
                        tier.getRangeEnd(), tier.getUnitSize(), tier.getUnitPrice(), tier.getRoundingMode(),
                        tier.getSortNo()))
                .toList());
        try {
            validator.validate(definition);
        } catch (IllegalArgumentException exception) {
            throw conflict("计价规则草稿配置无效");
        }
    }

    private void validateDefinition(PricingRuleDefinition definition) {
        try {
            validator.validate(definition);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private void copyRuleFields(PricingRule rule, PricingRuleWriteRequest request) {
        rule.setBaseFare(request.baseFare());
        rule.setIncludedDistanceM(request.includedDistanceM());
        rule.setMinimumFare(request.minimumFare());
    }

    private String normalizedName(String name) {
        return name.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "计价规则不存在");
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}