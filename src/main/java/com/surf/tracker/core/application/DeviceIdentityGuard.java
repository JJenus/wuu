package com.surf.tracker.core.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Serializes device registration attempts across the vehicle and tracker tables.
 *
 * <p>Both tables have their own unique constraint, so sequential checks alone can
 * race across tables. PostgreSQL transaction-scoped advisory locks close that race
 * across application instances without a schema change. H2/dev deployments skip the
 * lock; the coordinator-owned migration/registry remains the durable cross-database
 * guarantee.</p>
 *
 * <p><strong>Preconditions for a correct caller.</strong> Call this inside an
 * active transaction: the lock is transaction-scoped, so a caller without one gets
 * no protection. The post-lock re-check relies on the default
 * {@code READ_COMMITTED} isolation. Under {@code REPEATABLE READ} a transaction
 * that reads a table before acquiring the lock pins its snapshot, and the re-check
 * could then miss a row committed by the transaction that just released the lock;
 * such callers must acquire the lock before their first dependent read. The tracker
 * create path validates vehicle ownership before locking and therefore depends on
 * READ_COMMITTED. This method uses {@code hashtextextended}, which requires
 * PostgreSQL 11+.</p>
 */
@Component
public class DeviceIdentityGuard {

    @PersistenceContext
    private EntityManager entityManager;

    public void lockForRegistration(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String product = entityManager.unwrap(Session.class)
                .doReturningWork(connection -> connection.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equalsIgnoreCase(product)) {
            return;
        }
        entityManager.createNativeQuery(
                        "select pg_advisory_xact_lock(hashtextextended(cast(:deviceId as text), 0))")
                .setParameter("deviceId", deviceId)
                .getSingleResult();
    }
}
