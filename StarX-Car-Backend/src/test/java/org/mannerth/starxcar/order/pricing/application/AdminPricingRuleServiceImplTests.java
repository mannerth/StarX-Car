package org.mannerth.starxcar.order.pricing.application;

import org.mannerth.starxcar.order.domain.FareChargeType;
import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import org.mannerth.starxcar.order.domain.RoundingMode;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.pricing.domain.PricingRule;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleTier;
import org.mannerth.starxcar.order.pricing.domain.PricingRuleValidator;
import org.mannerth.starxcar.order.pricing.infrastructure.PricingRuleMapper;
import org.mannerth.starxcar.order.pricing.infrastructure.PricingRuleTierMapper;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.request.PricingTierWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;




@ExtendWith(MockitoExtension.class)
class AdminPricingRuleServiceImplTests {
    @Mock
    private PricingRuleMapper ruleMapper;
    @Mock
    private PricingRuleTierMapper tierMapper;
    @Mock
    private RideOrderMapper orderMapper;

    private AdminPricingRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminPricingRuleServiceImpl(ruleMapper, tierMapper, orderMapper, new PricingRuleValidator());
    }

    @Test
    void createsNextVersionAsDraftWithCompleteTiers() {
        given(ruleMapper.selectLatestVersionForUpdate("默认即时叫车规则")).willReturn(3);
        given(ruleMapper.insert(org.mockito.ArgumentMatchers.any(PricingRule.class))).willAnswer(invocation -> {
            invocation.getArgument(0, PricingRule.class).setId(7L);
            return 1;
        });
        given(tierMapper.insertBatch(any())).willReturn(7);

        PricingRuleResponse response = service.create(9L, request());

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.version()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(PricingRuleStatus.draft);
        assertThat(response.defaultRule()).isFalse();
        assertThat(response.tiers()).hasSize(7);
        verify(tierMapper).insertBatch(any());
    }

    @Test
    void rejectsPiecewiseRuleWithGapBeforeWriting() {
        PricingRuleWriteRequest invalid = new PricingRuleWriteRequest("规则", new BigDecimal("5.00"), 2000L,
                new BigDecimal("5.00"), new BigDecimal("5.00"), List.of(
                tier(FareChargeType.DISTANCE, 0, 2000L, 1000, "0.00", 10),
                tier(FareChargeType.DISTANCE, 3000, null, 1000, "3.00", 20),
                tier(FareChargeType.DRIVING_TIME, 0, null, 60, "0.20", 10),
                tier(FareChargeType.WAITING_TIME, 0, null, 60, "0.00", 10)));

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(9L, invalid));
        verify(ruleMapper, never()).insert((PricingRule) any());
    }

    @Test
    void updatesOnlyUnreferencedDraftAndReplacesAllTiers() {
        PricingRule rule = rule(7L, PricingRuleStatus.draft, false);
        given(ruleMapper.selectByIdForUpdate(7L)).willReturn(rule);
        given(ruleMapper.updateById(org.mockito.ArgumentMatchers.eq(rule))).willReturn(1);
        given(tierMapper.insertBatch(any())).willReturn(7);

        PricingRuleResponse response = service.updateDraft(9L, 7L, request());

        assertThat(response.status()).isEqualTo(PricingRuleStatus.draft);
        verify(tierMapper).deleteByRuleId(7L);
        verify(tierMapper).insertBatch(any());
    }

    @Test
    void refusesToUpdateReferencedDraft() {
        given(ruleMapper.selectByIdForUpdate(7L)).willReturn(rule(7L, PricingRuleStatus.draft, false));
        given(orderMapper.exists(any())).willReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.updateDraft(9L, 7L, request()));
        verify(tierMapper, never()).deleteByRuleId(7L);
        verify(ruleMapper, never()).updateById((PricingRule) any());
    }

    @Test
    void activatesDraftAndAtomicallyReplacesDefaultRule() {
        PricingRule rule = rule(7L, PricingRuleStatus.draft, false);
        List<PricingRuleTier> tiers = persistedTiers(7L);
        given(ruleMapper.selectByIdForUpdate(7L)).willReturn(rule);
        given(tierMapper.selectList(any())).willReturn(tiers);
        given(ruleMapper.lockActiveDefaultRuleIds()).willReturn(List.of(3L));
        given(ruleMapper.deactivateOtherDefaultRules(7L, 9L)).willReturn(1);
        given(ruleMapper.activateDraftRule(7L, 9L, true)).willReturn(1);

        PricingRuleResponse response = service.activate(9L, 7L, new PricingRuleActivationRequest(true));

        assertThat(response.status()).isEqualTo(PricingRuleStatus.active);
        assertThat(response.defaultRule()).isTrue();
        verify(ruleMapper).lockActiveDefaultRuleIds();
        verify(ruleMapper).deactivateOtherDefaultRules(7L, 9L);
        verify(ruleMapper).activateDraftRule(7L, 9L, true);
    }

    @Test
    void promotesEnabledNonDefaultRuleToDefault() {
        PricingRule rule = rule(7L, PricingRuleStatus.active, false);
        given(ruleMapper.selectByIdForUpdate(7L)).willReturn(rule);
        given(ruleMapper.lockActiveDefaultRuleIds()).willReturn(List.of(3L));
        given(ruleMapper.deactivateOtherDefaultRules(7L, 9L)).willReturn(1);
        given(ruleMapper.makeActiveRuleDefault(7L, 9L)).willReturn(1);

        PricingRuleResponse response = service.activate(9L, 7L, new PricingRuleActivationRequest(true));

        assertThat(response.status()).isEqualTo(PricingRuleStatus.active);
        assertThat(response.defaultRule()).isTrue();
        verify(ruleMapper).lockActiveDefaultRuleIds();
        verify(ruleMapper).deactivateOtherDefaultRules(7L, 9L);
        verify(ruleMapper).makeActiveRuleDefault(7L, 9L);
        verify(ruleMapper, never()).activateDraftRule(eq(7L), eq(9L), any(Boolean.class));
    }

    @Test
    void cannotArchiveCurrentDefaultRule() {
        given(ruleMapper.selectByIdForUpdate(7L)).willReturn(rule(7L, PricingRuleStatus.active, true));

        assertStatus(HttpStatus.CONFLICT, () -> service.archive(9L, 7L));
        verify(ruleMapper, never()).archiveNonDefaultRule(eq(7L), eq(9L));
    }

    private PricingRuleWriteRequest request() {
        return new PricingRuleWriteRequest("默认即时叫车规则", new BigDecimal("5.00"), 2000L,
                new BigDecimal("5.00"), new BigDecimal("5.00"), List.of(
                tier(FareChargeType.DISTANCE, 0, 2000L, 1000, "0.00", 10),
                tier(FareChargeType.DISTANCE, 2000, 12000L, 1000, "3.00", 20),
                tier(FareChargeType.DISTANCE, 12000, null, 1000, "1.20", 30),
                tier(FareChargeType.DRIVING_TIME, 0, 1800L, 60, "0.20", 10),
                tier(FareChargeType.DRIVING_TIME, 1800, null, 60, "0.50", 20),
                tier(FareChargeType.WAITING_TIME, 0, 300L, 60, "0.00", 10),
                tier(FareChargeType.WAITING_TIME, 300, null, 60, "0.50", 20)));
    }

    private PricingTierWriteRequest tier(FareChargeType type, long start, Long end, long unit, String price, int sort) {
        return new PricingTierWriteRequest(type, start, end, unit, new BigDecimal(price), RoundingMode.CEIL, sort);
    }

    private PricingRule rule(Long id, PricingRuleStatus status, boolean defaultRule) {
        PricingRule rule = new PricingRule();
        rule.setId(id);
        rule.setName("默认即时叫车规则");
        rule.setVersion(2);
        rule.setStatus(status);
        rule.setDefaultRule(defaultRule);
        rule.setBaseFare(new BigDecimal("5.00"));
        rule.setIncludedDistanceM(2000L);
        rule.setMinimumFare(new BigDecimal("5.00"));
        return rule;
    }

    private List<PricingRuleTier> persistedTiers(Long ruleId) {
        return request().tiers().stream().map(request -> {
            PricingRuleTier tier = new PricingRuleTier();
            tier.setId((long) request.sortNo());
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

    private void assertStatus(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }
}
