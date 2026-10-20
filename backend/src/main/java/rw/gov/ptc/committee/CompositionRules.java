package rw.gov.ptc.committee;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Composition rules evaluated against a proposed set of active seats. Every
 * threshold comes from configuration. ERROR issues block an appointment;
 * WARNING issues are shown but do not block.
 */
@Component
public class CompositionRules {

    public record Issue(String code, String severity, String message, Map<String, Object> params) {
    }

    public record Result(boolean compliant, List<Issue> issues) {

        public List<Issue> errors() {
            return issues.stream().filter(i -> "ERROR".equals(i.severity())).toList();
        }

        public List<Issue> warnings() {
            return issues.stream().filter(i -> "WARNING".equals(i.severity())).toList();
        }

        /** Throws 422 with the first blocking rule, listing all issues in params. */
        public void throwIfBlocked() {
            if (!errors().isEmpty()) {
                Issue first = errors().get(0);
                Map<String, Object> params = new LinkedHashMap<>(first.params());
                params.put("issues", issues);
                throw ApiException.rule(first.code(), first.message(), params);
            }
        }
    }

    /** A seat in the proposed composition. isNew marks seats being appointed now. */
    public record Seat(UUID userId, String role, int consecutiveTerm, boolean isNew) {
    }

    public record UserFacts(UUID id, String fullName, String officeHolderCode, boolean procurementOfficer,
                            boolean cbm, String status) {
    }

    private final JdbcTemplate jdbc;

    public CompositionRules(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, UserFacts> facts(Collection<UUID> userIds) {
        Map<UUID, UserFacts> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT u.id, u.full_name, u.office_holder_code, u.is_procurement_officer, u.status,
                       EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'CBM') AS cbm
                  FROM app_user u WHERE u.id = ANY(?)
                """, rs -> {
            UUID id = rs.getObject(1, UUID.class);
            out.put(id, new UserFacts(id, rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(6),
                    rs.getString(5)));
        }, (Object) userIds.toArray(new UUID[0]));
        return out;
    }

    public Result evaluate(Committee committee, List<Seat> seats, ConfigSnapshot config) {
        List<Issue> issues = new ArrayList<>();
        Map<UUID, UserFacts> facts = facts(seats.stream().map(Seat::userId).toList());
        Set<String> excluded = new HashSet<>(config.getStrings(ConfigKeys.EXCLUDED_OFFICES));
        int maxConsecutive = config.getInt(ConfigKeys.TERM_MAX_RENEWALS) + 1;

        Set<UUID> seen = new HashSet<>();
        Map<String, Integer> officerRoles = new HashMap<>();
        for (Seat s : seats) {
            UserFacts u = facts.get(s.userId());
            String name = u == null ? "?" : u.fullName();
            if (u == null) {
                issues.add(error("composition.unknown_user", "User not found in this institution", Map.of()));
                continue;
            }
            if (!seen.add(s.userId())) {
                issues.add(error("composition.already_member", name + " already holds a seat on this committee",
                        Map.of("user", name)));
            }
            if (!"MEMBER".equals(s.role())) {
                officerRoles.merge(s.role(), 1, Integer::sum);
            }
            if (s.isNew()) {
                if (u.cbm() || "CBM".equals(u.officeHolderCode())) {
                    issues.add(error("composition.cbm_cannot_be_member",
                            "The Chief Budget Manager constitutes committees and cannot be a member of one",
                            Map.of("user", name)));
                } else if (u.officeHolderCode() != null && excluded.contains(u.officeHolderCode())) {
                    issues.add(error("composition.excluded_office",
                            name + " holds an office (" + u.officeHolderCode() + ") excluded from committee membership",
                            Map.of("user", name, "office", u.officeHolderCode())));
                }
                if (!"ACTIVE".equals(u.status()) && !"INVITED".equals(u.status())) {
                    issues.add(error("composition.user_inactive", name + " is not an active user", Map.of("user", name)));
                }
                if (s.consecutiveTerm() > maxConsecutive) {
                    issues.add(error("composition.term_limit_reached",
                            name + " would serve consecutive term " + s.consecutiveTerm() + "; at most "
                                    + maxConsecutive + " consecutive terms are allowed",
                            Map.of("user", name, "term", s.consecutiveTerm(), "max", maxConsecutive)));
                }
            }
        }
        officerRoles.forEach((role, n) -> {
            if (n > 1) {
                issues.add(error("composition.role_taken", "Only one " + role + " may sit at a time", Map.of("role", role)));
            }
        });

        int count = seats.size();
        if (count > committee.size) {
            issues.add(error("composition.size_exceeded",
                    "The committee is constituted with " + committee.size + " members; this would make " + count,
                    Map.of("size", committee.size, "count", count)));
        }
        long pos = seats.stream().filter(s -> facts.containsKey(s.userId()) && facts.get(s.userId()).procurementOfficer()).count();
        boolean poMajority = pos * 2 > committee.size;
        if (poMajority && config.getBool(ConfigKeys.BLOCK_PO_MAJORITY)) {
            boolean addsPo = seats.stream().anyMatch(s -> s.isNew() && facts.containsKey(s.userId())
                    && facts.get(s.userId()).procurementOfficer());
            Issue issue = new Issue("composition.procurement_officer_majority", addsPo ? "ERROR" : "WARNING",
                    "Procurement officers may be members but must not form a majority: this would make " + pos
                            + " of " + committee.size + " members procurement officers (at most "
                            + ((committee.size - 1) / 2) + ")",
                    Map.of("procurementOfficers", pos, "size", committee.size, "max", (committee.size - 1) / 2));
            issues.add(issue);
        }

        long consecutive = seats.stream().filter(s -> s.consecutiveTerm() > 1).count();
        double share = config.getDecimal(ConfigKeys.TERM_CONSECUTIVE_WARNING_SHARE).doubleValue();
        if (consecutive > share * committee.size) {
            issues.add(warning("composition.consecutive_terms_majority",
                    consecutive + " of " + committee.size + " members would be serving consecutive terms",
                    Map.of("count", consecutive, "size", committee.size)));
        }
        if (count < committee.size) {
            issues.add(warning("composition.below_size", count + " of " + committee.size + " seats are filled",
                    Map.of("count", count, "size", committee.size)));
        }
        if (pos == 0) {
            issues.add(warning("composition.no_procurement_officer",
                    "The procurement officer is a permanent member and acts as secretary; none is appointed", Map.of()));
        }
        seats.stream().filter(s -> "SECRETARY".equals(s.role())).findFirst().ifPresent(sec -> {
            UserFacts u = facts.get(sec.userId());
            if (u != null && !u.procurementOfficer()) {
                issues.add(warning("composition.secretary_not_procurement_officer",
                        "The secretary should be a procurement officer", Map.of("user", u.fullName())));
            }
        });
        boolean compliant = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new Result(compliant, issues);
    }

    /** Consecutive-term number for a new seat starting on termStart. */
    public static int consecutiveTerm(List<Membership> previous, LocalDate termStart) {
        for (Membership m : previous) {
            LocalDate effectiveEnd = m.endedAt == null ? m.termEnd
                    : rw.gov.ptc.common.Time.localDate(m.endedAt).isBefore(m.termEnd)
                    ? rw.gov.ptc.common.Time.localDate(m.endedAt) : m.termEnd;
            if (!effectiveEnd.isBefore(termStart.minusDays(1)) && m.termStart.isBefore(termStart)) {
                return m.consecutiveTerm + 1;
            }
        }
        return 1;
    }

    private static Issue error(String code, String message, Map<String, Object> params) {
        return new Issue(code, "ERROR", message, params);
    }

    private static Issue warning(String code, String message, Map<String, Object> params) {
        return new Issue(code, "WARNING", message, params);
    }
}
