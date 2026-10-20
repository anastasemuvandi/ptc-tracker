package rw.gov.ptc.tenancy;

import rw.gov.ptc.common.ApiException;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant and actor of the current unit of work. Set from the verified
 * access token by the security filter, or explicitly by background jobs via
 * {@link #callAs}. {@link TenantAwareDataSource} copies it into the
 * PostgreSQL session variables that the RLS policies read.
 */
public final class TenantContext {

    public record Scope(UUID institutionId, UUID userId, boolean platformAdmin) {
    }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Scope scope) {
        CURRENT.set(scope);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Scope current() {
        return CURRENT.get();
    }

    public static UUID institutionOrNull() {
        Scope s = CURRENT.get();
        return s == null ? null : s.institutionId();
    }

    public static UUID userOrNull() {
        Scope s = CURRENT.get();
        return s == null ? null : s.userId();
    }

    public static UUID requireInstitution() {
        UUID id = institutionOrNull();
        if (id == null) {
            throw ApiException.forbidden("no_institution", "This action requires an institution context");
        }
        return id;
    }

    /** Run work as a given tenant (system jobs); restores the previous scope. */
    public static <T> T callAs(Scope scope, Supplier<T> work) {
        Scope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(Scope scope, Runnable work) {
        callAs(scope, () -> {
            work.run();
            return null;
        });
    }
}
