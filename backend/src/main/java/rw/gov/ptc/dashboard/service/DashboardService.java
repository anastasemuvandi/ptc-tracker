package rw.gov.ptc.dashboard.service;

import rw.gov.ptc.auth.api.AuthPrincipal;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only aggregates for the role dashboards. All queries run under RLS,
 * so every figure is confined to the caller's institution.
 */
public interface DashboardService {

    Map<String, Object> member(AuthPrincipal p);

    Map<String, Object> chair(UUID committeeId);

    Map<String, Object> cbm();

    Map<String, Object> summary(LocalDate from, LocalDate to, UUID committeeId);

    boolean committeeVisible(UUID committeeId);
}
