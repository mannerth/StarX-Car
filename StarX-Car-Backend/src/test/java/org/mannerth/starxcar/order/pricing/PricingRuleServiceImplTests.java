package org.mannerth.starxcar.order.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PricingRuleServiceImplTests {
    private PricingRuleMapper pricingRuleMapper;
    private PricingRuleTierMapper pricingRuleTierMapper;
    private PricingRuleService service;

    @BeforeEach
    void setUp() {
        pricingRuleMapper = mock(PricingRuleMapper.class);
        pricingRuleTierMapper = mock(PricingRuleTierMapper.class);
        service = new PricingRuleServiceImpl(pricingRuleMapper, pricingRuleTierMapper, new PricingRuleValidator());
    }

    @Test
    void loadsTheOnlyActiveDefaultRuleAndSortsItsTiers() {
        PricingRule rule = PricingFixtures.ruleEntity();
        List<PricingRuleTier> tiers = PricingFixtures.defaultRule().tiers().stream()
                .map(PricingFixtures::tierEntity)
                .sorted((left, right) -> Integer.compare(right.getSortNo(), left.getSortNo()))
                .toList();
        given(pricingRuleMapper.selectList(any())).willReturn(List.of(rule));
        given(pricingRuleTierMapper.selectList(any())).willReturn(tiers);

        PricingRuleDefinition result = service.getDefaultActiveRule();

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.tiers()).extracting(PricingTierDefinition::id)
                .containsExactly(11L, 12L, 13L, 21L, 22L, 31L, 32L);
    }

    @Test
    void rejectsMissingOrMultipleDefaultRulesAsUnavailable() {
        given(pricingRuleMapper.selectList(any())).willReturn(List.of());
        assertUnavailable(() -> service.getDefaultActiveRule());

        given(pricingRuleMapper.selectList(any())).willReturn(List.of(PricingFixtures.ruleEntity(), PricingFixtures.ruleEntity()));
        assertUnavailable(() -> service.getDefaultActiveRule());
    }

    @Test
    void rejectsInvalidStoredTierConfigurationAsUnavailable() {
        PricingRule rule = PricingFixtures.ruleEntity();
        List<PricingRuleTier> invalidTiers = PricingFixtures.defaultRule().tiers().stream()
                .map(PricingFixtures::tierEntity)
                .toList();
        invalidTiers.get(1).setRangeStart(2_001L);
        given(pricingRuleMapper.selectList(any())).willReturn(List.of(rule));
        given(pricingRuleTierMapper.selectList(any())).willReturn(invalidTiers);

        assertUnavailable(() -> service.getDefaultActiveRule());
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
