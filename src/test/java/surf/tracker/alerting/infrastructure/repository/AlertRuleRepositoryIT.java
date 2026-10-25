package com.surf.tracker.alerting.infrastructure.repository;

import com.surf.tracker.alerting.domain.AlertRuleTestBuilder;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AlertRuleRepositoryIT {

    private static final long ORG_ID = 1L;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Test
    void findByRuleKey_existingRule_returnsRule() {
        // given
        AlertRule rule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("test-rule-001")
            .build();
        entityManager.persistAndFlush(rule);

        // when
        Optional<AlertRule> found = alertRuleRepository.findByRuleKeyAndOrganizationId("test-rule-001", ORG_ID);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getRuleKey()).isEqualTo("test-rule-001");
    }

    @Test
    void findByRuleKey_nonExistentRule_returnsEmpty() {
        // when
        Optional<AlertRule> found = alertRuleRepository.findByRuleKeyAndOrganizationId("non-existent", ORG_ID);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void existsByRuleKey_existingRule_returnsTrue() {
        // given
        AlertRule rule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("test-rule-002")
            .build();
        entityManager.persistAndFlush(rule);

        // when
        boolean exists = alertRuleRepository.existsByRuleKeyAndOrganizationId("test-rule-002", ORG_ID);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByRuleKey_nonExistentRule_returnsFalse() {
        // when
        boolean exists = alertRuleRepository.existsByRuleKeyAndOrganizationId("non-existent", ORG_ID);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    void findByIsEnabled_enabledOnly_returnsFiltered() {
        // given
        AlertRule enabledRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("enabled-rule")
            .enabled(true)
            .build();

        AlertRule disabledRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("disabled-rule")
            .enabled(false)
            .build();

        entityManager.persist(enabledRule);
        entityManager.persist(disabledRule);
        entityManager.flush();

        // when
        List<AlertRule> enabledRules = alertRuleRepository.findByIsEnabledAndOrganizationId(true, ORG_ID);
        List<AlertRule> disabledRules = alertRuleRepository.findByIsEnabledAndOrganizationId(false, ORG_ID);

        // then
        assertThat(enabledRules).hasSize(1);
        assertThat(enabledRules.get(0).getRuleKey()).isEqualTo("enabled-rule");

        assertThat(disabledRules).hasSize(1);
        assertThat(disabledRules.get(0).getRuleKey()).isEqualTo("disabled-rule");
    }

    @Test
    void findActiveRulesForVehicle_returnsRulesForVehicle() {
        // given
        AlertRule rule1 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-001")
            .vehicleId("vehicle-001")
            .enabled(true)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-002")
            .vehicleId("vehicle-001")
            .enabled(true)
            .build();

        AlertRule rule3 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-003")
            .vehicleId("vehicle-002") // Different vehicle
            .enabled(true)
            .build();

        entityManager.persist(rule1);
        entityManager.persist(rule2);
        entityManager.persist(rule3);
        entityManager.flush();

        // when
        List<AlertRule> vehicleRules = alertRuleRepository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", ORG_ID);

        // then
        assertThat(vehicleRules).hasSize(2);
        assertThat(vehicleRules)
            .extracting("ruleKey")
            .containsExactlyInAnyOrder("rule-001", "rule-002");
    }

    @Test
    void findVehiclesWithActiveRules_returnsDistinctVehicles() {
        // given
        AlertRule rule1 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-001")
            .vehicleIds(Set.of("vehicle-001", "vehicle-002"))
            .enabled(true)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-002")
            .vehicleIds(Set.of("vehicle-002", "vehicle-003"))
            .enabled(true)
            .build();

        entityManager.persist(rule1);
        entityManager.persist(rule2);
        entityManager.flush();

        // when
        Set<String> vehicles = alertRuleRepository.findVehiclesWithActiveRules(ORG_ID);

        // then
        assertThat(vehicles).containsExactlyInAnyOrder("vehicle-001", "vehicle-002", "vehicle-003");
    }

    @Test
    void findByRuleType_returnsFilteredRules() {
        // given
        AlertRule speedRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("speed-rule")
            .ruleType(AlertRuleType.SPEED)
            .build();

        AlertRule timeRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("time-rule")
            .ruleType(AlertRuleType.TIME)
            .build();

        entityManager.persist(speedRule);
        entityManager.persist(timeRule);
        entityManager.flush();

        // when
        List<AlertRule> speedRules = alertRuleRepository.findByRuleTypeAndOrganizationId(AlertRuleType.SPEED, ORG_ID);
        List<AlertRule> timeRules = alertRuleRepository.findByRuleTypeAndOrganizationId(AlertRuleType.TIME, ORG_ID);

        // then
        assertThat(speedRules).hasSize(1);
        assertThat(speedRules.get(0).getRuleKey()).isEqualTo("speed-rule");

        assertThat(timeRules).hasSize(1);
        assertThat(timeRules.get(0).getRuleKey()).isEqualTo("time-rule");
    }

    @Test
    void deleteByRuleKey_existingRule_deletesIt() {
        // given
        AlertRule rule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("to-delete")
            .build();
        entityManager.persistAndFlush(rule);

        // when
        alertRuleRepository.deleteByRuleKeyAndOrganizationId("to-delete", ORG_ID);

        // then
        Optional<AlertRule> found = alertRuleRepository.findByRuleKeyAndOrganizationId("to-delete", ORG_ID);
        assertThat(found).isEmpty();
    }
}
