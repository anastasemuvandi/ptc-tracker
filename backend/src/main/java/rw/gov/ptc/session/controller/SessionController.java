package rw.gov.ptc.session.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.session.dto.CarryOver;
import rw.gov.ptc.session.dto.RecordActivity;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.service.ActivityService;
import rw.gov.ptc.session.service.SchedulingService;
import rw.gov.ptc.session.component.SessionAccess;
import rw.gov.ptc.session.dto.AddItem;
import rw.gov.ptc.session.dto.ApologyRequest;
import rw.gov.ptc.session.dto.Mark;
import rw.gov.ptc.session.dto.Quorum;
import rw.gov.ptc.session.dto.SessionRequest;
import rw.gov.ptc.session.dto.StatusRequest;
import rw.gov.ptc.session.entity.AbsenceApology;
import rw.gov.ptc.session.entity.AgendaItem;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.service.AgendaService;
import rw.gov.ptc.session.service.AttendanceService;
import rw.gov.ptc.session.service.SessionService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;
    private final SessionAccess sessionAccess;
    private final AgendaService agenda;
    private final AttendanceService attendance;
    private final ActivityService activities;
    private final SchedulingService scheduling;

    public SessionController(SessionService sessions, SessionAccess sessionAccess, AgendaService agenda,
                             AttendanceService attendance, ActivityService activities, SchedulingService scheduling) {
        this.scheduling = scheduling;
        this.activities = activities;
        this.sessions = sessions;
        this.sessionAccess = sessionAccess;
        this.agenda = agenda;
        this.attendance = attendance;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) UUID committeeId,
                                          @RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        return sessions.list(committeeId, from, to);
    }

    @PostMapping
    public CommitteeSession create(@RequestBody SessionRequest req) {
        return scheduling.schedule(req);
    }

    @GetMapping("/{id}")
    public Map<String, Object> view(@PathVariable UUID id) {
        return sessions.view(id);
    }

    @PutMapping("/{id}")
    public CommitteeSession update(@PathVariable UUID id, @RequestBody SessionRequest req) {
        return sessions.update(id, req);
    }

    @PostMapping("/{id}/status")
    public CommitteeSession status(@PathVariable UUID id, @RequestBody StatusRequest req) {
        return sessions.changeStatus(id, req.status());
    }

    @GetMapping("/{id}/quorum")
    public Quorum quorum(@PathVariable UUID id) {
        return sessionAccess.quorum(sessionAccess.readable(id));
    }

    @PostMapping("/{id}/agenda")
    public AgendaItem addItem(@PathVariable UUID id, @RequestBody AddItem req) {
        return agenda.add(id, req);
    }

    /** Activities for a file before any session exists (scheduling page). */
    @GetMapping("/activity-options")
    public Map<String, Object> fileActivityOptions(@RequestParam UUID fileId) {
        return activities.options(fileId);
    }

    @GetMapping("/{id}/activity-options")
    public Map<String, Object> activityOptions(@PathVariable UUID id, @RequestParam UUID fileId) {
        return activities.options(id, fileId);
    }

    @PostMapping("/{id}/activities/{activityId}")
    public AgendaActivity recordActivity(@PathVariable UUID id, @PathVariable UUID activityId, @RequestBody RecordActivity req) {
        return activities.record(id, activityId, req);
    }

    @GetMapping("/{id}/carry-over")
    public List<Map<String, Object>> carryOverCandidates(@PathVariable UUID id) {
        return activities.carryOverCandidates(id);
    }

    @PostMapping("/{id}/carry-over")
    public List<AgendaActivity> carryOver(@PathVariable UUID id, @RequestBody CarryOver req) {
        return agenda.carryOver(id, req.activityIds());
    }

    @DeleteMapping("/{id}/agenda/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        agenda.remove(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/agenda/order")
    public List<AgendaItem> reorder(@PathVariable UUID id, @RequestBody List<UUID> order) {
        return agenda.reorder(id, order);
    }

    @PutMapping("/{id}/attendance/{membershipId}")
    public Map<String, Object> mark(@PathVariable UUID id, @PathVariable UUID membershipId,
                                    @RequestBody Mark req) {
        return attendance.mark(id, membershipId, req);
    }

    @PostMapping("/{id}/apologies")
    public AbsenceApology apologise(@PathVariable UUID id, @RequestBody ApologyRequest req) {
        return attendance.apologise(id, req);
    }
}
