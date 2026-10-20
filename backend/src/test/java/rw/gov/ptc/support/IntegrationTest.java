package rw.gov.ptc.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Boots the application against a real PostgreSQL 16 with the production
 * role layout: migrations as the owner, runtime as ptc_app / ptc_auth, so
 * row-level security is genuinely exercised.
 */
@SpringBootTest
@Import(TestBeans.class)
public abstract class IntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ptc")
                    .withUsername("ptc_owner")
                    .withPassword("owner-test")
                    .withInitScript("db/init-roles.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws Exception {
        Path keys = Files.createTempDirectory("ptc-keys");
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "ptc_app");
        r.add("spring.datasource.password", () -> "app-test");
        r.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        r.add("spring.flyway.user", () -> "ptc_owner");
        r.add("spring.flyway.password", () -> "owner-test");
        r.add("ptc.auth.datasource.url", POSTGRES::getJdbcUrl);
        r.add("ptc.auth.datasource.username", () -> "ptc_auth");
        r.add("ptc.auth.datasource.password", () -> "auth-test");
        r.add("ptc.auth.keys-dir", keys::toString);
        r.add("ptc.auth.generate-keys-if-missing", () -> "true");
        // Suites sign in many users from one address within a minute.
        r.add("ptc.auth.login-attempts-per-minute-per-ip", () -> "10000");
    }
}
