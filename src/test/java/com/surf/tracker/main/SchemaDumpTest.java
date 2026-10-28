package com.surf.tracker.main;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.h2.jdbcx.JdbcDataSource;

class SchemaDumpTest {

    @Test
    void dumpHibernateSchema() throws Exception {
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

        String exportTarget = System.getProperty("schema.dump.file",
                "target/generated-schema.sql");

        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:schemadump;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");

        Map<String, Object> props = new HashMap<>();
        props.put(AvailableSettings.DATASOURCE, (DataSource) ds);
        props.put(AvailableSettings.HBM2DDL_AUTO, "create-drop");
        props.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_ACTION, "create");
        props.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_CREATE_TARGET, exportTarget);
        props.put(AvailableSettings.SHOW_SQL, "true");
        props.put(AvailableSettings.FORMAT_SQL, "true");

        EntityManagerFactory emf = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean() {
            {
                setDataSource(ds);
                setPersistenceProviderClass(org.hibernate.jpa.HibernatePersistenceProvider.class);
                setJpaPropertyMap(props);
                setPackagesToScan("com.surf.tracker");
                setLoadTimeWeaver(null);
                afterPropertiesSet();
            }
        }.getObject();

        emf.createEntityManager().close();
        emf.close();

        String sql = new String(Files.readAllBytes(Paths.get(exportTarget)), StandardCharsets.UTF_8);
        try (PrintWriter pw = new PrintWriter(System.out)) {
            pw.println("===== SCHEMA DUMP START =====");
            pw.println(sql);
            pw.println("===== SCHEMA DUMP END =====");
        }
    }
}
