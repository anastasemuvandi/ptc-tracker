package rw.gov.ptc.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Superuser connection for test fixtures, bypassing RLS deliberately. */
public final class Owner {

    private Owner() {
    }

    public static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                IntegrationTest.POSTGRES.getJdbcUrl(), "ptc_owner", "owner-test"));
    }
}
