package com.surf.tracker.alerting.application;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.surf.tracker.alerting.domain.IAlertRule;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.GeofenceRule;
import com.surf.tracker.alerting.domain.RuleStateStore;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.application.factory.AlertRuleFactory;
import com.surf.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.alerting.exception.AlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AlertingEngine {
    private final VehicleRuleCacheService vehicleRuleCacheService;
    private final EventPublisher eventPublisher;
    private final AlertRuleEvaluationService evaluationService;
    private final AlertRuleFactory ruleFactory;
    private final RuleStateStore stateStore;
    private final AlertDeduplicator deduplicator;
    private final MetricsRegistry metrics;
    private final Logger logger = LoggerFactory.getLogger(AlertingEngine.class);

    public AlertingEngine(
            EventPublisher eventPublisher,
            AlertRuleEvaluationService evaluationService,
            VehicleRuleCacheService vehicleRuleCacheService,
            AlertRuleFactory ruleFactory,
            RuleStateStore stateStore,
            AlertDeduplicator deduplicator,
            MetricsRegistry metrics) {
        this.vehicleRuleCacheService = vehicleRuleCacheService;
        this.eventPublisher = eventPublisher;
        this.evaluationService = evaluationService;
        this.ruleFactory = ruleFactory;
        this.stateStore = stateStore;
        this.deduplicator = deduplicator;
        this.metrics = metrics;
    }

    public void processVehicleUpdate(String vehicleId, LocationPoint newLocation) {
        if (vehicleId == null || newLocation == null) {
            throw new ValidationException(
                    "ALERT_INVALID_INPUT",
                    "Vehicle and location cannot be null"
            );
        }

        Long organizationId = TenantContext.getCurrentOrgId();

        if (!vehicleRuleCacheService.hasRulesCached(vehicleId)) {
            logger.debug("Vehicle {} has no active rules (cached index), skipping", vehicleId);
            return;
        }

        if (!vehicleRuleCacheService.hasActiveRules(vehicleId, organizationId)) {
            return;
        }

        List<AlertRule> vehicleRules = vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId, organizationId);

        if (vehicleRules.isEmpty()) {
            return;
        }

        logger.debug("Processing {} rules for vehicle {}", vehicleRules.size(), vehicleId);

        for (AlertRule rule : vehicleRules) {
            try {
                IAlertRule domainRule = ruleFactory.createDomainRule(rule, vehicleId);
                if (domainRule == null) {
                    continue;
                }

                AlertDetectedEvent alert = evaluationService.evaluateRule(domainRule, vehicleId, newLocation);

                if (alert != null && deduplicator.tryAcquire(alert)) {
                    logger.info("Alert triggered: {} for vehicle {}", alert.getRuleKey(), vehicleId);
                    eventPublisher.publish(alert);
                    metrics.increment("alert.triggered", "type", alert.getAlertType().name());
                }

                if (domainRule instanceof GeofenceRule geo) {
                    stateStore.setGeofenceWasInside(rule.getRuleKey(), vehicleId, geo.isWasInside());
                }
            } catch (AlertException e) {
                logger.error("Alert evaluation error for rule {}: {}", rule.getRuleKey(), e.getMessage());
                metrics.increment("alert.error", "rule", rule.getRuleKey());
            } catch (Exception e) {
                logger.error("Unexpected error evaluating rule {}: {}", rule.getRuleKey(), e.getMessage());
            }
        }
    }

    public void invalidateVehicleCache(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
    }

    public void invalidateAllCache() {
        vehicleRuleCacheService.invalidateAllVehicleRules();
    }

    public void refreshVehicleRules(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
        vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId, TenantContext.getCurrentOrgId());
    }
}
