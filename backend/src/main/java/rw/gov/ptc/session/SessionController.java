package rw.gov.ptc.session;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public record StatusRequest(String status) {
    }

    private final SessionService sessions;
    private final SessionAccess sessionAccess;
    private final AgendaService agenda;
    private final AttendanceService attendance;

    public SessionController(SessionService sessions, SessionAccess sessionAccess, AgendaService agenda,
                             AttendanceService attendance) {
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
    public CommitteeSession create(@RequestBody SessionService.SessionRequest req) {
        return sessions.create(req);
    }

    @GetMapping("/{id}")
    public Map<String, Object> view(@PathVariable UUID id) {
        return sessions.view(id);
    }

    @PutMapping("/{id}")
    public CommitteeSession update(@PathVariable UUID id, @RequestBody SessionService.SessionRequest req) {
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
    public AgendaItem addItem(@PathVariable UUID id, @RequestBody AgendaService.AddItem req) {
        return agenda.add(id, req);
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
                                    @RequestBody AttendanceService.Mark req) {
        return attendance.mark(id, membershipId, req);
    }

    @PostMapping("/{id}/apologies")
    public AbsenceApology apologise(@PathVariable UUID id, @RequestBody AttendanceService.ApologyRequest req) {
        return attendance.apologise(id, req);
    }
}
