package com.surf.tracker.main;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Keep vendor histories aligned without editing already-applied H2 migrations. */
class MigrationParityTest {
    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("migration %s exists", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String sql) {
        return sql.replaceAll("(?m)--[^\\r\\n]*", "").replaceAll("\\s+", " ").trim();
    }

    @Test void postgresBaseline_matchesH2ExceptCheckedStringEnums() throws IOException {
        // given: PostgreSQL has no H2-style inline ENUM type
        String h2 = resource("/db/migration/V1__init_schema.sql");
        Pattern inlineEnum = Pattern.compile("(\\w+) enum \\(([^)]*)\\)");
        // when: define the only intended vendor difference
        String expected = inlineEnum.matcher(h2).replaceAll(match -> {
            String column = match.group(1);
            return column + " varchar(255) CHECK (" + column + " IN (" + match.group(2) + "))";
        });
        // then: includes indexes, foreign keys, nullability and all business tables
        assertThat(normalize(resource("/db/postgresql/V1__init_schema.sql"))).isEqualTo(normalize(expected));
    }

    @Test void securityMigrations_bothVendors_haveIdenticalSeedsAndRevocation() throws IOException {
        // given / when / then
        for (String migration : new String[]{"V2__seed_permissions_and_super_admin.sql", "V3__session_organization.sql", "V4__alert_event_outbox.sql"}) {
            assertThat(resource("/db/postgresql/" + migration)).isEqualTo(resource("/db/migration/" + migration));
        }
    }
}
