package rw.gov.ptc.committee.service;

import rw.gov.ptc.committee.component.CompositionRules;
import rw.gov.ptc.committee.dto.Appointment;
import rw.gov.ptc.committee.dto.CreateCommittee;
import rw.gov.ptc.committee.dto.NewPeriod;
import rw.gov.ptc.committee.dto.Replacement;
import rw.gov.ptc.committee.dto.UpdateCommittee;
import rw.gov.ptc.committee.entity.Committee;
import rw.gov.ptc.committee.entity.CommitteePeriod;
import rw.gov.ptc.committee.entity.Membership;
import rw.gov.ptc.committee.entity.MembershipReplacement;
import rw.gov.ptc.committee.entity.TrackedTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Committee constitution by the Chief Budget Manager. */
public interface CommitteeService {

    Committee get(UUID id);

    Committee create(CreateCommittee req);

    Committee update(UUID id, UpdateCommittee req);

    /** The latest period: the one running now, or the last one if it has lapsed. */
    Optional<CommitteePeriod> currentPeriod(UUID committeeId);

    List<CommitteePeriod> periods(UUID committeeId);

    /** Reappoint the committee for a new period (same name, new dates). */
    CommitteePeriod reappoint(UUID committeeId, NewPeriod req);

    List<Membership> active(UUID committeeId);

    CompositionRules.Result composition(UUID committeeId);

    /** Composition after removing some seats and appointing others. */
    CompositionRules.Result simulate(UUID committeeId, Set<UUID> outgoing, List<Appointment> appointments);

    Map<String, Object> appoint(UUID committeeId, Appointment a);

    Membership end(UUID committeeId, UUID membershipId, String reason);

    /**
     * Replace one or all members. Replacement requires sound, recorded
     * reasons; replacing ALL members also opens a tracked task to notify the
     * regulator within the configured period.
     */
    Map<String, Object> replace(UUID committeeId, Replacement req);

    List<MembershipReplacement> replacements(UUID committeeId);

    List<Map<String, Object>> members(UUID committeeId);

    List<Map<String, Object>> candidates(UUID committeeId);

    TrackedTask completeTask(UUID taskId, String reference, String note);
}
