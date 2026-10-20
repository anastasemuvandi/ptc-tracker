package rw.gov.ptc.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Two runtime connection pools with different database roles:
 * <ul>
 *   <li>primary (ptc_app): wrapped in {@link TenantAwareDataSource}, subject
 *       to row-level security; used by JPA and all domain code;</li>
 *   <li>auth (ptc_auth): used only inside the auth module for credentials,
 *       tokens and identity lookups before a tenant is known.</li>
 * </ul>
 * Migrations run as a third, owning role (spring.flyway.user).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties appDataSourceProperties) {
        HikariDataSource pool = appDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        pool.setPoolName("ptc-app");
        return new TenantAwareDataSource(pool);
    }

    @Bean
    @ConfigurationProperties("ptc.auth.datasource")
    public DataSourceProperties authDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource authDataSource(@Qualifier("authDataSourceProperties") DataSourceProperties props) {
        HikariDataSource pool = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        pool.setPoolName("ptc-auth");
        pool.setMaximumPoolSize(5);
        return pool;
    }
}
