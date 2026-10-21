package rw.gov.ptc.calendar.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.calendar.dto.HolidayRequest;
import rw.gov.ptc.calendar.service.WorkingDayCalendar;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final JdbcTemplate jdbc;
    private final Access access;
    private final AuditService audit;
    private final WorkingDayCalendar calendar;

    public HolidayController(JdbcTemplate jdbc, Access access, AuditService audit, WorkingDayCalendar calendar) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.calendar = calendar;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam int year) {
        return Rows.camel(jdbc.queryForList("""
                SELECT id, holiday_date, name, kind,
                       CASE WHEN institution_id IS NULL THEN 'PLATFORM' ELSE 'INSTITUTION' END AS scope
                  FROM holiday WHERE extract(year FROM holiday_date) = ?
                 ORDER BY holiday_date, scope
                """, year));
    }

    @GetMapping("/working-days")
    public Map<String, Object> addWorkingDays(@RequestParam LocalDate from, @RequestParam int days) {
        return Map.of("from", from, "days", days, "result", calendar.addWorkingDays(from, days));
    }

    @PostMapping
    @Transactional
    public Map<String, Object> add(@RequestBody HolidayRequest req) {
        boolean platform = "PLATFORM".equals(req.scope());
        if (platform) {
            access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        } else {
            access.requireAny(InstitutionRole.INSTITUTION_ADMIN);
        }
        if (req.date() == null || req.name() == null || req.name().isBlank()) {
            throw ApiException.badRequest("holiday.invalid", "Date and name are required");
        }
        String kind = "REMOVE".equals(req.kind()) && !platform ? "REMOVE" : "ADD";
        UUID id = Ids.newId();
        jdbc.update("INSERT INTO holiday (id, institution_id, holiday_date, name, kind) VALUES (?, ?, ?, ?, ?)",
                id, platform ? null : TenantContext.requireInstitution(), Date.valueOf(req.date()), req.name(), kind);
        audit.created("holiday", id, req);
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        var rows = jdbc.queryForList("SELECT institution_id, holiday_date, name FROM holiday WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Holiday");
        }
        if (rows.get(0).get("institution_id") == null) {
            access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        } else {
            access.requireAny(InstitutionRole.INSTITUTION_ADMIN);
        }
        jdbc.update("DELETE FROM holiday WHERE id = ?", id);
        audit.record("DELETE", "holiday", id, rows.get(0), null);
        return ResponseEntity.noContent().build();
    }
}
