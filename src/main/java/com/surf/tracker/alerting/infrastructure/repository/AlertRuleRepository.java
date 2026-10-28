package com.surf.tracker.alerting.infrastructure.repository;

import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByRuleKeyAndOrganizationId(String ruleKey, Long organizationId);

    List<AlertRule> findByIsEnabledAndOrganizationId(boolean isEnabled, Long organizationId);

    Page<AlertRule> findByIsEnabledAndOrganizationId(boolean isEnabled, Long organizationId, Pageable pageable);

    List<AlertRule> findByRuleTypeAndOrganizationId(AlertRuleType ruleType, Long organizationId);

    Page<AlertRule> findByRuleTypeAndOrganizationId(AlertRuleType ruleType, Long organizationId, Pageable pageable);

    List<AlertRule> findByRuleTypeAndIsEnabledAndOrganizationId(AlertRuleType ruleType, boolean isEnabled, Long organizationId);

    Page<AlertRule> findByRuleTypeAndIsEnabledAndOrganizationId(AlertRuleType ruleType, boolean isEnabled, Long organizationId, Pageable pageable);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true AND ar.organizationId = :organizationId " +
            "ORDER BY ar.priority DESC")
    List<AlertRule> findActiveRulesOrderedByPriority(@Param("organizationId") Long organizationId);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true AND ar.organizationId = :organizationId " +
            "ORDER BY ar.priority DESC")
    Page<AlertRule> findActiveRulesOrderedByPriority(@Param("organizationId") Long organizationId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "vehicleIds")
    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "AND ar.organizationId = :organizationId " +
            "AND (:vehicleId MEMBER OF ar.vehicleIds OR :vehicleId IS NULL) " +
            "ORDER BY ar.priority DESC")
    List<AlertRule> findActiveRulesForVehicleAndOrganizationId(@Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "AND ar.organizationId = :organizationId " +
            "AND (:vehicleId MEMBER OF ar.vehicleIds OR :vehicleId IS NULL)")
    Page<AlertRule> findActiveRulesForVehicleAndOrganizationId(@Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT DISTINCT vid FROM AlertRule ar JOIN ar.vehicleIds vid WHERE ar.isEnabled = true AND ar.organizationId = :organizationId")
    Set<String> findVehiclesWithActiveRules(@Param("organizationId") Long organizationId);

    boolean existsByRuleKeyAndOrganizationId(String ruleKey, Long organizationId);

    void deleteByRuleKeyAndOrganizationId(String ruleKey, Long organizationId);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.organizationId = :organizationId AND " +
            "(CAST(:search AS string) IS NULL OR LOWER(ar.ruleName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
            "AND (:ruleType IS NULL OR ar.ruleType = :ruleType) " +
            "AND (:enabled IS NULL OR ar.isEnabled = :enabled)")
    Page<AlertRule> searchAlertRules(
            @Param("organizationId") Long organizationId,
            @Param("search") String search,
            @Param("ruleType") AlertRuleType ruleType,
            @Param("enabled") Boolean enabled,
            Pageable pageable);

    Page<AlertRule> findByOrganizationId(Long organizationId, Pageable pageable);

    long countByOrganizationId(Long organizationId);
}
