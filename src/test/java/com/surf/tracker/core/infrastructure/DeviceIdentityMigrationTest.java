package com.surf.tracker.core.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** Real SQL/transactions; optional PostgreSQL uses an exclusively owned disposable schema. */
class DeviceIdentityMigrationTest {
    static Stream<String> databases() {
        return System.getProperty("fleet.test.postgres.url") == null
                ? Stream.of("h2") : Stream.of("h2", "postgres");
    }

    @ParameterizedTest
    @MethodSource("databases")
    void migration_existingIdentities_backfillsAndValidates(String vendor) throws Exception {
        try (Database db = new Database(vendor)) {
            db.migrate("6");
            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('vehicle', 'legacy')");
            db.jdbc.update("insert into vehicles(vehicle_id) values ('without-device')");
            db.jdbc.update("insert into trackers(tracker_id, device_id) values ('tracker', 'registered')");

            db.migrate(null);

            assertThat(db.jdbc.queryForList("select device_id from device_identity order by device_id", String.class))
                    .containsExactly("legacy", "registered");
            assertThat(db.jdbc.queryForObject("select owner_kind from device_identity where device_id='legacy'", String.class))
                    .isEqualTo("VEHICLE");
            assertThat(db.flyway(null).migrate().migrationsExecuted).isZero();
            db.flyway(null).validate();
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void migration_existingCollision_failsWithoutChangingSourceRows(String vendor) throws Exception {
        try (Database db = new Database(vendor)) {
            db.migrate("6");
            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('vehicle', 'collision')");
            db.jdbc.update("insert into trackers(tracker_id, device_id) values ('tracker', 'collision')");

            assertThatThrownBy(() -> db.migrate(null)).hasRootCauseInstanceOf(SQLException.class);

            assertThat(db.jdbc.queryForObject("select device_id from vehicles where vehicle_id='vehicle'", String.class))
                    .isEqualTo("collision");
            assertThat(db.jdbc.queryForObject("select device_id from trackers where tracker_id='tracker'", String.class))
                    .isEqualTo("collision");
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void directWrites_crossTableDuplicate_rejectsBothDirections(String vendor) throws Exception {
        try (Database db = new Database(vendor)) {
            db.migrate(null);
            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('vehicle', 'vehicle-device')");
            db.jdbc.update("insert into trackers(tracker_id, device_id) values ('tracker', 'tracker-device')");

            assertThatThrownBy(() -> db.jdbc.update(
                    "insert into trackers(tracker_id, device_id) values ('other-tracker', 'vehicle-device')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.jdbc.update(
                    "insert into vehicles(vehicle_id, device_id) values ('other-vehicle', 'tracker-device')"))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(db.jdbc.queryForObject("select count(*) from device_identity", Integer.class)).isEqualTo(2);
            assertThat(db.jdbc.queryForObject("select count(*) from vehicles", Integer.class)).isEqualTo(1);
            assertThat(db.jdbc.queryForObject("select count(*) from trackers", Integer.class)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void updateAndDelete_identityChanges_releaseOnlyTheOriginalOwner(String vendor) throws Exception {
        try (Database db = new Database(vendor)) {
            db.migrate(null);
            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('vehicle', 'legacy')");
            db.jdbc.update("insert into trackers(tracker_id, device_id) values ('tracker', 'registered')");

            assertThatThrownBy(() -> db.jdbc.update("update trackers set device_id='legacy' where tracker_id='tracker'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(db.jdbc.queryForObject("select device_id from trackers where tracker_id='tracker'", String.class))
                    .isEqualTo("registered");
            assertThat(db.jdbc.queryForObject("select count(*) from device_identity", Integer.class)).isEqualTo(2);

            db.jdbc.update("update trackers set tracker_id='renamed', model='changed' where tracker_id='tracker'");
            assertThat(db.jdbc.queryForObject("select owner_id from device_identity where device_id='registered'", String.class))
                    .isEqualTo("renamed");
            db.jdbc.update("update vehicles set device_id=null where vehicle_id='vehicle'");
            db.jdbc.update("update trackers set device_id='legacy' where tracker_id='renamed'");
            db.jdbc.update("delete from trackers where tracker_id='renamed'");
            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('replacement', 'legacy')");

            assertThat(db.jdbc.queryForObject("select count(*) from device_identity", Integer.class)).isEqualTo(1);
            assertThat(db.jdbc.queryForObject("select owner_id from device_identity where device_id='legacy'", String.class))
                    .isEqualTo("replacement");
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void transaction_rolledBackOrInvalidInsert_doesNotLeakRegistration(String vendor) throws Exception {
        try (Database db = new Database(vendor)) {
            db.migrate(null);
            try (Connection connection = db.source.getConnection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("insert into vehicles(vehicle_id, device_id) values ('rolled-back', 'available')");
                }
                connection.rollback();
            }
            assertThatThrownBy(() -> db.jdbc.update("insert into trackers(tracker_id, device_id, vehicle_id) "
                    + "values ('invalid', 'available', 'missing')")).isInstanceOf(DataIntegrityViolationException.class);

            db.jdbc.update("insert into vehicles(vehicle_id, device_id) values ('valid', 'available')");

            assertThat(db.jdbc.queryForObject("select count(*) from device_identity", Integer.class)).isEqualTo(1);
            assertThat(db.jdbc.queryForObject("select owner_id from device_identity where device_id='available'", String.class))
                    .isEqualTo("valid");
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void concurrentDirectWrites_sameDevice_waitsThenRejectsSecondOwner(String vendor) throws Exception {
        try (Database db = new Database(vendor); var executor = Executors.newSingleThreadExecutor()) {
            db.migrate(null);
            try (Connection first = db.source.getConnection()) {
                first.setAutoCommit(false);
                try (var statement = first.createStatement()) {
                    statement.executeUpdate("insert into vehicles(vehicle_id, device_id) values ('first', 'contended')");
                }
                var attempted = new CountDownLatch(1);
                var second = executor.submit(() -> {
                    try (Connection connection = db.source.getConnection(); var statement = connection.createStatement()) {
                        attempted.countDown();
                        statement.executeUpdate("insert into trackers(tracker_id, device_id) values ('second', 'contended')");
                        return "accepted";
                    } catch (SQLException failure) {
                        return failure.getSQLState();
                    }
                });
                try {
                    assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    first.commit();
                    assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("23505");
                } finally {
                    first.rollback();
                }
            }
            assertThat(db.jdbc.queryForObject("select count(*) from device_identity", Integer.class)).isEqualTo(1);
            assertThat(db.jdbc.queryForObject("select count(*) from trackers", Integer.class)).isZero();
        }
    }

    private static final class Database implements AutoCloseable {
        final String vendor;
        final String schema = "fleet_test_" + UUID.randomUUID().toString().replace("-", "");
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;

        Database(String vendor) {
            this.vendor = vendor;
            if (vendor.equals("postgres")) {
                String url = System.getProperty("fleet.test.postgres.url");
                // Only a caller-owned local test server; never application database configuration.
                if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) {
                    throw new IllegalArgumentException("PostgreSQL tests require a disposable loopback server");
                }
                var admin = new JdbcTemplate(new DriverManagerDataSource(url));
                admin.execute("create schema " + schema);
                source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
            } else {
                source = new DriverManagerDataSource("jdbc:h2:mem:" + schema + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
            }
            jdbc = new JdbcTemplate(source);
        }

        Flyway flyway(String target) {
            var config = Flyway.configure().dataSource(source)
                    .locations(vendor.equals("postgres") ? "classpath:db/postgresql" : "classpath:db/migration");
            if (target != null) config.target(target);
            return config.load();
        }

        void migrate(String target) { flyway(target).migrate(); }

        @Override
        public void close() {
            if (vendor.equals("postgres")) jdbc.execute("drop schema " + schema + " cascade");
            else jdbc.execute("shutdown");
        }
    }
}
