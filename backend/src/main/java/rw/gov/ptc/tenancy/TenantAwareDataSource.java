package rw.gov.ptc.tenancy;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sets app.institution_id / app.user_id / app.platform_admin on every
 * connection checkout from {@link TenantContext}, and clears them when the
 * connection is returned to the pool. Every query the application runs —
 * JPA or JDBC — therefore executes under the row-level security policies
 * for the authenticated tenant. With no context the variables are empty
 * and tenant tables return no rows.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String APPLY =
            "select set_config('app.institution_id', ?, false), "
                    + "set_config('app.user_id', ?, false), "
                    + "set_config('app.platform_admin', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scoped(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scoped(obtainTargetDataSource().getConnection(username, password));
    }

    private Connection scoped(Connection connection) throws SQLException {
        TenantContext.Scope scope = TenantContext.current();
        try {
            apply(connection,
                    scope == null || scope.institutionId() == null ? "" : scope.institutionId().toString(),
                    scope == null || scope.userId() == null ? "" : scope.userId().toString(),
                    scope != null && scope.platformAdmin() ? "on" : "");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    if (!connection.isClosed()) {
                        if (!connection.getAutoCommit()) {
                            connection.rollback();
                        }
                        apply(connection, "", "", "");
                    }
                } catch (SQLException ignored) {
                    // the pool validates connections; a broken one is discarded
                }
                connection.close();
                return null;
            }
            if ("unwrap".equals(method.getName()) || "isWrapperFor".equals(method.getName())) {
                return method.invoke(connection, args);
            }
            try {
                return method.invoke(connection, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    /**
     * set_config(..., false) is session-scoped but transactional, so outside
     * autocommit the change is committed immediately — otherwise a later
     * rollback of the caller's work would silently revert it.
     */
    private static void apply(Connection connection, String institution, String user, String admin)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(APPLY)) {
            ps.setString(1, institution);
            ps.setString(2, user);
            ps.setString(3, admin);
            ps.execute();
        }
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }
}
