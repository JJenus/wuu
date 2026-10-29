package com.surf.tracker.main;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT {

    @Test
    void flywayMigrations_applyCleanly_andSchemaValidates() {
        // given - H2 matching the runtime PostgreSQL-compatible mode
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:flywayit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL");

        // when - apply the single coherent migration history
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        var result = flyway.migrate();

        // then - all migrations applied, schema present
        assertThat(result.migrationsExecuted).isEqualTo(6);
        assertThat(result.success).isTrue();
        assertThat(new org.springframework.jdbc.core.JdbcTemplate(ds)
                .queryForObject("select count(*) from alert_event_outbox", Long.class)).isZero();

        // and - Hibernate validates every entity against the migrated schema
        EntityManagerFactory emf = buildValidatingEmf(ds);
        emf.createEntityManager().close();
        emf.close();
    }

    private EntityManagerFactory buildValidatingEmf(DataSource ds) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> entityClasses = scanner.findCandidateComponents("com.surf.tracker")
                .stream()
                .map(bd -> {
                    try {
                        return Class.forName(bd.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toList());

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        Map<String, Object> props = new HashMap<>();
        props.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        bean.setDataSource(ds);
        bean.setJpaVendorAdapter(adapter);
        bean.setJpaPropertyMap(props);
        bean.setPackagesToScan("com.surf.tracker");
        bean.setLoadTimeWeaver(null);
        bean.afterPropertiesSet();
        return bean.getObject();
    }
}
