package com.example.store.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests that require a real PostgreSQL instance. Uses the singleton container pattern —
 * one container shared across all test classes for performance, stopped via JVM shutdown hook when all tests complete.
 *
 * <p>Both {@code @DataJpaTest} and {@code @SpringBootTest} test classes can extend this class to inherit the shared
 * container and dynamic property configuration.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("store_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.contexts", () -> "test");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
