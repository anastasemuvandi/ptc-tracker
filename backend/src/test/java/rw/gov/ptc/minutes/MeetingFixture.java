package rw.gov.ptc.minutes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A held session with a type A and a type C item, written as the owner
 * (RLS bypassed) so these tests do not depend on other modules' services.
 */
final class MeetingFixture {

    final String abbr;
    final UUID institution = Ids.newId();
    final UUID committee = Ids.newId();
    final UUID session = Ids.newId();
    final UUID chair, secretary, dissenter, member, absent, cbm;
    final Map<UUID, UUID> membership = new java.util.HashMap<>();
    final UUID fileA = Ids.newId(), fileC = Ids.newId(), contract = Ids.newId();
    final String sessionNumber, fileNumberA, fileNumberC;
    final String dissentReason = "The second-ranked bidder's abnormally low price was not adequately explained in writing.";

    MeetingFixture() {
        JdbcTemplate db = Owner.jdbc();
        abbr = "MF" + (System.nanoTime() % 1_000_000);
        sessionNumber = abbr + "-A 2026/007";
        fileNumberA = "F-" + abbr + "/0001";
        fileNumberC = "F-" + abbr + "/0002";
        db.update("INSERT INTO institution (id, name, abbreviation, institution_type, minutes_letterhead) VALUES (?, ?, ?, 'AGENCY', ?)",
                institution, "Rwanda Test Agency " + abbr, abbr, "REPUBLIC OF RWANDA\nRwanda Test Agency");
        chair = user(db, "Alice Chair");
        secretary = user(db, "Bob Secretary");
        dissenter = user(db, "Claire Dissent");
        member = user(db, "David Member");
        absent = user(db, "Eve Absent");
        cbm = user(db, "Frank Budget");
        db.update("INSERT INTO user_role (id, user_id, institution_id, role) VALUES (?, ?, ?, 'CBM')", Ids.newId(), cbm, institution);
        db.update("INSERT INTO committee (id, institution_id, name, code, size) VALUES (?, ?, 'Tender Committee A', ?, 5)",
                committee, institution, abbr + "-A");
        seat(db, chair, "CHAIRPERSON");
        seat(db, secretary, "SECRETARY");
        seat(db, dissenter, "MEMBER");
        seat(db, member, "MEMBER");
        seat(db, absent, "MEMBER");
        db.update("""
                INSERT INTO committee_session (id, institution_id, committee_id, session_number, kind, scheduled_at, location,
                                               status, committee_size, config_snapshot)
                VALUES (?, ?, ?, ?, 'ORDINARY', now() - interval '2 hours', 'Board room', 'HELD', 5,
                        '{"values": {"quorum.rule": {"numerator": 3, "denominator": 5}}}')
                """, session, institution, committee, sessionNumber);
        for (UUID u : List.of(chair, secretary, dissenter, member)) {
            db.update("INSERT INTO attendance (id, institution_id, session_id, membership_id, user_id, status, recorded_by) VALUES (?, ?, ?, ?, ?, 'PRESENT', ?)",
                    Ids.newId(), institution, session, membership.get(u), u, secretary);
        }
        db.update("INSERT INTO attendance (id, institution_id, session_id, membership_id, user_id, status, recorded_by) VALUES (?, ?, ?, ?, ?, 'ABSENT_WITH_PERMISSION', ?)",
                Ids.newId(), institution, session, membership.get(absent), absent, secretary);
        db.update("INSERT INTO absence_apology (id, institution_id, session_id, user_id, reason, timely) VALUES (?, ?, ?, ?, 'Official mission', true)",
                Ids.newId(), institution, session, absent);

        // Type A file with evaluation progress
        db.update("""
                INSERT INTO proc_file (id, institution_id, committee_id, file_number, item_type, tender_ref, title,
                                       procurement_type, date_received, bid_validity_expiry, procurement_officer_id)
                VALUES (?, ?, ?, ?, 'A', 'TND-2026-0042', 'Supply of laboratory equipment', 'GOODS', current_date - 20, ?, ?)
                """, fileA, institution, committee, fileNumberA, java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(18)), secretary);
        UUID eval = Ids.newId();
        db.update("INSERT INTO evaluation (id, institution_id, file_id, criteria_locked_at, criteria_locked_by, config_snapshot) VALUES (?, ?, ?, now(), ?, '{}')",
                eval, institution, fileA, chair);
        UUID a1 = stage(db, eval, "A1", 1, "Two of three bidders passed eligibility");
        UUID a2 = stage(db, eval, "A2", 2, "Both bidders technically responsive");
        stage(db, eval, "A3", 3, "Arithmetic check complete");
        bidder(db, a1, "BIDDER-001", "PASS", null, null, null);
        bidder(db, a1, "BIDDER-002", "PASS", null, null, null);
        bidder(db, a1, "BIDDER-003", "FAIL", "BID_SECURITY_OR_DECLARATION", "Bid security issued for 60 days instead of 120 days.", null);
        bidder(db, a2, "BIDDER-002", "PASS", null, null, 1);
        bidder(db, a2, "BIDDER-001", "PASS", null, null, 2);
        db.update("""
                INSERT INTO abnormal_price_check (id, institution_id, file_id, bidder_ref, direction, request_ref, request_date,
                       explanation_summary, explanation_date, analysis, doubt_lifted, decision, decision_reasons, recorded_by)
                VALUES (?, ?, ?, 'BIDDER-002', 'ABNORMALLY_LOW', 'REF/2026/77', current_date - 6, 'Stock purchased in 2025',
                        current_date - 3, 'Supplier invoices support the price', true, 'ACCEPTED', 'Doubt lifted by evidence', ?)
                """, Ids.newId(), institution, fileA, secretary);
        db.update("""
                INSERT INTO clarification (id, institution_id, file_id, bidder_ref, issued_on, subject, response_received_on,
                       treatment, effect, recorded_by)
                VALUES (?, ?, ?, 'BIDDER-002', current_date - 8, 'Delivery schedule', current_date - 6,
                        'Explanation accepted; no change to the bid', 'CLARIFIED', ?)
                """, Ids.newId(), institution, fileA, secretary);
        UUID itemA = agenda(db, fileA, "A", 1);
        coi(db, itemA, fileA, chair, false, null);
        coi(db, itemA, fileA, secretary, false, null);
        coi(db, itemA, fileA, dissenter, false, null);
        coi(db, itemA, fileA, member, true, "Brother-in-law works for BIDDER-001");
        UUID decA = Ids.newId();
        db.update("""
                INSERT INTO decision (id, institution_id, session_id, agenda_item_id, file_id, item_type, recommendation_code,
                       recommendation_text, recommended_bidder_ref, contract_value, currency, present_count, eligible_voters,
                       required_votes, votes_for, votes_against, votes_abstained, status, issued_at, config_snapshot)
                VALUES (?, ?, ?, ?, ?, 'A', 'AWARD', 'Award to the lowest evaluated responsive bidder', 'BIDDER-002',
                        245000000.00, 'RWF', 4, 3, 2, 2, 1, 0, 'ISSUED', now(), '{"values": {"voting.rule": {"numerator": 3, "denominator": 5}}}')
                """, decA, institution, session, itemA, fileA);
        vote(db, decA, chair, "FOR", null);
        vote(db, decA, secretary, "FOR", null);
        vote(db, decA, dissenter, "AGAINST", dissentReason);

        // Type C file: third amendment, cumulative over the ceiling
        db.update("""
                INSERT INTO contract (id, institution_id, contract_ref, tender_ref, title, procurement_type, currency, original_value,
                                      original_completion_date, current_completion_date)
                VALUES (?, ?, ?, 'TND-2025-0100', 'Construction of district office', 'WORKS', 'RWF', 1000000000.00,
                        current_date + 40, current_date + 40)
                """, contract, institution, "CT-" + abbr);
        UUID priorFile = Ids.newId();
        db.update("""
                INSERT INTO proc_file (id, institution_id, committee_id, file_number, item_type, contract_id, title, date_received, closed_at)
                VALUES (?, ?, ?, ?, 'C', ?, 'Addendum 1', current_date - 200, now())
                """, priorFile, institution, committee, "F-" + abbr + "/0000", contract);
        db.update("""
                INSERT INTO amendment_request (id, institution_id, file_id, contract_id, currency, requested_value, description,
                       status, approved_value, created_by)
                VALUES (?, ?, ?, ?, 'RWF', 120000000.00, 'Additional drainage works', 'APPROVED', 120000000.00, ?)
                """, Ids.newId(), institution, priorFile, contract, secretary);
        db.update("""
                INSERT INTO proc_file (id, institution_id, committee_id, file_number, item_type, contract_id, title, date_received, procurement_officer_id)
                VALUES (?, ?, ?, ?, 'C', ?, 'Addendum 2 — access road', current_date - 5, ?)
                """, fileC, institution, committee, fileNumberC, contract, secretary);
        db.update("""
                INSERT INTO amendment_request (id, institution_id, file_id, contract_id, currency, requested_value, description,
                       affects_substance, substance_justification, status, created_by)
                VALUES (?, ?, ?, ?, 'RWF', 100000000.00, 'Access road to the site', false,
                        'Same works category and purpose; quantities only', 'RECOMMENDED', ?)
                """, Ids.newId(), institution, fileC, contract, secretary);
        UUID itemC = agenda(db, fileC, "C", 2);
        UUID decC = Ids.newId();
        db.update("""
                INSERT INTO decision (id, institution_id, session_id, agenda_item_id, file_id, item_type, recommendation_code,
                       recommendation_text, present_count, eligible_voters, required_votes, votes_for, status, issued_at, config_snapshot)
                VALUES (?, ?, ?, ?, ?, 'C', 'ESCALATE_TO_SUPERVISING_AUTHORITY', 'Escalate: cumulative value exceeds the ceiling',
                        4, 4, 3, 4, 'ISSUED', now(), '{"values": {"amendment.ceilingPct": 20, "amendment.absoluteCeilings": {"RWF": "1000000000"}}}')
                """, decC, institution, session, itemC, fileC);
        for (UUID u : List.of(chair, secretary, dissenter, member)) {
            vote(db, decC, u, "FOR", null);
        }
    }

    private UUID user(JdbcTemplate db, String name) {
        UUID id = Ids.newId();
        db.update("INSERT INTO app_user (id, institution_id, email, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                id, institution, name.toLowerCase().replace(' ', '.') + "." + abbr.toLowerCase() + "@example.rw", name);
        return id;
    }

    private void seat(JdbcTemplate db, UUID user, String role) {
        UUID id = Ids.newId();
        db.update("""
                INSERT INTO committee_membership (id, institution_id, committee_id, user_id, role, term_start, term_end)
                VALUES (?, ?, ?, ?, ?, current_date - 100, current_date + 30)
                """, id, institution, committee, user, role);
        membership.put(user, id);
    }

    private UUID stage(JdbcTemplate db, UUID eval, String code, int seq, String outcome) {
        UUID id = Ids.newId();
        db.update("""
                INSERT INTO evaluation_stage (id, institution_id, evaluation_id, file_id, stage_code, sequence, status, outcome,
                       completed_at, completed_by)
                VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED', ?, now(), ?)
                """, id, institution, eval, fileA, code, seq, outcome, secretary);
        return id;
    }

    private void bidder(JdbcTemplate db, UUID stage, String ref, String result, String req, String reason, Integer rank) {
        db.update("""
                INSERT INTO bidder_result (id, institution_id, stage_id, file_id, bidder_ref, result, failed_requirement,
                       failure_reason, rank, recorded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Ids.newId(), institution, stage, fileA, ref, result, req, reason, rank, secretary);
    }

    private UUID agenda(JdbcTemplate db, UUID file, String type, int position) {
        UUID id = Ids.newId();
        db.update("INSERT INTO agenda_item (id, institution_id, session_id, file_id, item_type, position, status) VALUES (?, ?, ?, ?, ?, ?, 'DISCUSSED')",
                id, institution, session, file, type, position);
        return id;
    }

    private void coi(JdbcTemplate db, UUID item, UUID file, UUID user, boolean interest, String details) {
        db.update("""
                INSERT INTO coi_declaration (id, institution_id, session_id, agenda_item_id, file_id, user_id, has_interest,
                       interest_kind, details, step_up_verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, Ids.newId(), institution, session, item, file, user, interest, interest ? "INDIRECT" : null, details);
    }

    private void vote(JdbcTemplate db, UUID decision, UUID user, String choice, String reason) {
        db.update("""
                INSERT INTO vote (id, institution_id, decision_id, membership_id, user_id, choice, dissent_reason, step_up_verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """, Ids.newId(), institution, decision, membership.get(user), user, choice, reason);
    }

    /** Act as a user: tenant scope + security principal. */
    void as(UUID user, Runnable work) {
        Set<InstitutionRole> roles = user.equals(cbm) ? Set.of(InstitutionRole.CBM) : Set.of();
        List<AuthPrincipal.CommitteeGrant> grants = membershipRole(user) == null ? List.of()
                : List.of(new AuthPrincipal.CommitteeGrant(committee, CommitteeRole.valueOf(membershipRole(user))));
        AuthPrincipal p = new AuthPrincipal(user, institution, roles, grants, 1, Ids.newId(), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        try {
            TenantContext.runAs(new TenantContext.Scope(institution, user, false), work);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String membershipRole(UUID user) {
        if (user.equals(chair)) return "CHAIRPERSON";
        if (user.equals(secretary)) return "SECRETARY";
        if (membership.containsKey(user)) return "MEMBER";
        return null;
    }
}
