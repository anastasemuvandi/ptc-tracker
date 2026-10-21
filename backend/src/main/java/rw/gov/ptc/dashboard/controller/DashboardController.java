package rw.gov.ptc.dashboard.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.dashboard.service.DashboardService;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DashboardController {

    private final DashboardService dashboards;
    private final Access access;

    public DashboardController(DashboardService dashboards, Access access) {
        this.dashboards = dashboards;
        this.access = access;
    }

    @GetMapping("/api/dashboard/member")
    public Map<String, Object> member() {
        TenantContext.requireInstitution();
        return dashboards.member(access.principal());
    }

    @GetMapping("/api/dashboard/chair")
    public Map<String, Object> chair(@RequestParam UUID committeeId) {
        AuthPrincipal p = access.principal();
        boolean officer = p.committeeRole(committeeId).filter(CommitteeRole::isOfficer).isPresent();
        if (!officer && !p.has(InstitutionRole.CBM) && !p.has(InstitutionRole.READ_ONLY)) {
            throw ApiException.forbidden("committee.officer_required", "Committee officers only");
        }
        requireCommitteeVisible(committeeId);
        return dashboards.chair(committeeId);
    }

    @GetMapping("/api/dashboard/cbm")
    public Map<String, Object> cbm() {
        access.requireAny(InstitutionRole.CBM, InstitutionRole.READ_ONLY, InstitutionRole.INSTITUTION_ADMIN);
        return dashboards.cbm();
    }

    @GetMapping("/api/reports/summary")
    public Map<String, Object> summary(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) UUID committeeId) {
        return dashboards.summary(fromOrDefault(from), toOrDefault(to), reportScope(committeeId));
    }

    @GetMapping("/api/reports/summary.csv")
    public ResponseEntity<byte[]> summaryCsv(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) UUID committeeId) {
        LocalDate f = fromOrDefault(from);
        LocalDate t = toOrDefault(to);
        Map<String, Object> data = dashboards.summary(f, t, reportScope(committeeId));
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("PTC Tracker report,").append(f).append(",").append(t).append("\n\n");
        section(csv, "Files by type", data.get("filesByType"),
                List.of("itemType", "received", "recommendationsIssued", "closed"));
        section(csv, "Sessions", data.get("sessions"),
                List.of("committeeCode", "held", "cancelled", "extraordinaryHeld", "shortNoticeHeld"));
        section(csv, "Attendance", data.get("attendance"),
                List.of("fullName", "committeeCode", "role", "sessionsHeld", "present", "absentWithPermission",
                        "onLeave", "absentUnjustified", "ratePct"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("ptc-report-" + f + "-" + t + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** CBM, read-only and admins see the institution; officers only their own committee. */
    private UUID reportScope(UUID committeeId) {
        AuthPrincipal p = access.principal();
        if (p.has(InstitutionRole.CBM) || p.has(InstitutionRole.READ_ONLY) || p.has(InstitutionRole.INSTITUTION_ADMIN)) {
            if (committeeId != null) {
                requireCommitteeVisible(committeeId);
            }
            return committeeId;
        }
        if (committeeId == null || p.committeeRole(committeeId).filter(CommitteeRole::isOfficer).isEmpty()) {
            throw ApiException.forbidden("committee.officer_required",
                    "Committee officers may report on their own committee only");
        }
        return committeeId;
    }

    /** RLS hides other institutions' committees; absence means not yours. */
    private void requireCommitteeVisible(UUID committeeId) {
        TenantContext.requireInstitution();
        if (!dashboards.committeeVisible(committeeId)) {
            throw ApiException.notFound("Committee");
        }
    }

    @SuppressWarnings("unchecked")
    private static void section(StringBuilder csv, String title, Object rows, List<String> columns) {
        csv.append(title).append("\n").append(String.join(",", columns)).append("\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) rows) {
            csv.append(String.join(",", columns.stream().map(c -> cell(row.get(c))).toList())).append("\n");
        }
        csv.append("\n");
    }

    private static String cell(Object v) {
        if (v == null) {
            return "";
        }
        String s = v.toString();
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            s = "'" + s; // spreadsheet formula injection guard
        }
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static LocalDate fromOrDefault(LocalDate from) {
        return from != null ? from : Time.today().withDayOfYear(1);
    }

    private static LocalDate toOrDefault(LocalDate to) {
        return to != null ? to : Time.today();
    }
}
