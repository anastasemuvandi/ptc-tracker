package rw.gov.ptc.committee;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.security.Access;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CommitteeController {

    public record EndRequest(String reason) {
    }

    public record CheckRequest(List<UUID> outgoingMembershipIds, List<CommitteeService.Appointment> appointments,
                               UUID userId, String role) {
    }

    public record CompleteTask(String reference, String note) {
    }

    private final CommitteeService service;
    private final CommitteeRepository committees;
    private final TrackedTaskRepository tasks;
    private final ConfigService config;
    private final Access access;

    public CommitteeController(CommitteeService service, CommitteeRepository committees, TrackedTaskRepository tasks,
                               ConfigService config, Access access) {
        this.service = service;
        this.committees = committees;
        this.tasks = tasks;
        this.config = config;
        this.access = access;
    }

    @GetMapping("/committees")
    public List<Map<String, Object>> list() {
        var quorum = config.snapshot().getFraction(ConfigKeys.QUORUM_RULE);
        boolean all = access.canReadAllCommittees();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Committee c : committees.findAllByOrderByCodeAsc()) {
            var role = access.committeeRole(c.id);
            if (!all && role.isEmpty()) {
                continue;
            }
            Map<String, Object> m = summary(c);
            m.put("quorumRequired", quorum.ceilOf(c.size));
            m.put("myRole", role.map(Enum::name).orElse(null));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> summary(Committee c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("name", c.name);
        m.put("code", c.code);
        m.put("size", c.size);
        m.put("status", c.status);
        m.put("regulatorAuthRef", c.regulatorAuthRef);
        m.put("regulatorAuthDate", c.regulatorAuthDate);
        m.put("version", c.version);
        m.put("activeMembers", service.active(c.id).size());
        return m;
    }

    @PostMapping("/committees")
    public Committee create(@RequestBody CommitteeService.CreateCommittee req) {
        return service.create(req);
    }

    @GetMapping("/committees/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        access.requireCanRead(id);
        Committee c = service.get(id);
        Map<String, Object> m = summary(c);
        m.put("quorumRequired", config.snapshot().getFraction(ConfigKeys.QUORUM_RULE).ceilOf(c.size));
        m.put("myRole", access.committeeRole(id).map(Enum::name).orElse(null));
        m.put("members", service.members(id));
        return m;
    }

    @PutMapping("/committees/{id}")
    public Committee update(@PathVariable UUID id, @RequestBody CommitteeService.UpdateCommittee req) {
        return service.update(id, req);
    }

    @GetMapping("/committees/{id}/composition")
    public CompositionRules.Result composition(@PathVariable UUID id) {
        access.requireCanRead(id);
        return service.composition(id);
    }

    @GetMapping("/committees/{id}/candidates")
    public List<Map<String, Object>> candidates(@PathVariable UUID id) {
        access.requireCbm();
        return service.candidates(id);
    }

    /** Dry run: what would the composition look like after these changes? */
    @PostMapping("/committees/{id}/members/check")
    public CompositionRules.Result check(@PathVariable UUID id, @RequestBody CheckRequest req) {
        access.requireCbm();
        List<CommitteeService.Appointment> appointments = new ArrayList<>(
                req.appointments() == null ? List.of() : req.appointments());
        if (req.userId() != null) {
            appointments.add(new CommitteeService.Appointment(req.userId(), req.role(), null, null, null, null));
        }
        return service.simulate(id, new HashSet<>(req.outgoingMembershipIds() == null ? List.of() : req.outgoingMembershipIds()),
                appointments);
    }

    @PostMapping("/committees/{id}/members")
    public Map<String, Object> appoint(@PathVariable UUID id, @RequestBody CommitteeService.Appointment req) {
        return service.appoint(id, req);
    }

    @PostMapping("/committees/{id}/members/{membershipId}/end")
    public Membership end(@PathVariable UUID id, @PathVariable UUID membershipId, @RequestBody EndRequest req) {
        return service.end(id, membershipId, req.reason());
    }

    @PostMapping("/committees/{id}/replacements")
    public Map<String, Object> replace(@PathVariable UUID id, @RequestBody CommitteeService.Replacement req) {
        return service.replace(id, req);
    }

    @GetMapping("/committees/{id}/replacements")
    public List<MembershipReplacement> replacements(@PathVariable UUID id) {
        access.requireCanRead(id);
        return service.replacements(id);
    }

    @GetMapping("/tasks")
    public List<TrackedTask> tasks(@RequestParam(required = false) String status) {
        List<TrackedTask> all = status == null ? tasks.findAllByOrderByCreatedAtDesc()
                : tasks.findByStatusOrderByDueDateAsc(status);
        if (access.canReadAllCommittees()) {
            return all;
        }
        return all.stream().filter(t -> t.committeeId != null
                && access.committeeRole(t.committeeId).map(CommitteeRole::isOfficer).orElse(false)).toList();
    }

    @PostMapping("/tasks/{id}/complete")
    public TrackedTask complete(@PathVariable UUID id, @RequestBody CompleteTask req) {
        return service.completeTask(id, req.reference(), req.note());
    }
}
