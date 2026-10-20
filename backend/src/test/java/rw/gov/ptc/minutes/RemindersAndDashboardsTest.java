package rw.gov.ptc.minutes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.dashboard.DashboardController;
import rw.gov.ptc.notification.NotificationService;
import rw.gov.ptc.reminder.ReminderScanner;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
class RemindersAndDashboardsTest extends IntegrationTest {

    @Autowired
    ReminderScanner scanner;
    @Autowired
    DashboardController dashboards;
    @Autowired
    NotificationService notifications;
    @Autowired
    rw.gov.ptc.notification.NotificationController notificationApi;

    @Test
    void inAppNotificationsAndPushSubscriptions() {
        MeetingFixture f = new MeetingFixture();
        f.as(f.member, () -> {
            assertThat(notificationApi.vapidPublicKey().get("publicKey")).hasSizeGreaterThan(80);
            notificationApi.subscribe(new rw.gov.ptc.notification.NotificationController.SubscriptionRequest(
                    "https://push.example.invalid/sub/" + f.abbr, Map.of("p256dh", "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U", "auth", "tBHItJI5svbpez7KI4CCXg")),
                    "test-agent");
            notifications.notify(f.member, "TASK_DUE", List.of("Send report", "2026-11-01"), "/n/task/x", null, null, "t1");
            List<Map<String, Object>> mine = notificationApi.list(true, 10);
            assertThat(mine).hasSize(1);
            notificationApi.read((UUID) mine.get(0).get("id"));
            assertThat(notificationApi.list(true, 10)).isEmpty();
            notificationApi.unsubscribe("https://push.example.invalid/sub/" + f.abbr);
        });
        f.as(f.chair, () -> assertThat(notificationApi.list(false, 10)).isEmpty());
    }

    @Test
    void reminderScanIsIdempotentAndCreatesValidityTaskOnce() {
        MeetingFixture f = new MeetingFixture();
        // Fixture: bids expire in 18 days; regulator lead 15 → request due in 3 days (inside the 10-day alert window).
        Runnable scan = () -> TenantContext.runAs(new TenantContext.Scope(f.institution, null, false),
                () -> scanner.scanInstitution(Time.today()));
        scan.run();
        scan.run();
        JdbcTemplate db = Owner.jdbc();
        List<Map<String, Object>> due = db.queryForList(
                "SELECT user_id, count(*) AS n FROM notification WHERE kind = 'BID_VALIDITY_REQUEST_DUE' AND entity_id = ? GROUP BY user_id",
                f.fileA);
        // Chair, secretary (also the procurement officer) — one each.
        assertThat(due).hasSize(2).allSatisfy(r -> assertThat(((Number) r.get("n")).intValue()).isEqualTo(1));
        assertThat(db.queryForObject("SELECT count(*) FROM tracked_task WHERE kind = 'BID_VALIDITY_EXTENSION_REQUEST' AND entity_id = ?",
                Integer.class, f.fileA)).isEqualTo(1);
        // Cumulative amendment at 22% > 20% ceiling, completion in 40 days < 45 lead: escalation alert incl. CBM.
        assertThat(db.queryForObject("SELECT count(*) FROM notification WHERE kind = 'AMENDMENT_ESCALATION_LEAD' AND user_id = ?",
                Integer.class, f.cbm)).isEqualTo(1);
        // Terms end in 30 days (< 60): every member and the CBM are told, once per membership.
        assertThat(db.queryForObject("SELECT count(*) FROM notification WHERE kind = 'TERM_EXPIRY' AND user_id = ?",
                Integer.class, f.cbm)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT count(DISTINCT link) FROM notification WHERE institution_id = ? AND link NOT LIKE '/n/%'",
                Integer.class, f.institution)).isZero();
        // Title in the recipient's language.
        db.update("UPDATE app_user SET locale = 'fr' WHERE id = ?", f.member);
        TenantContext.runAs(new TenantContext.Scope(f.institution, null, false), () ->
                notifications.notify(f.member, "SESSION_UPCOMING", List.of("S-1", "2026-11-02", "PTC-A", "09:00"),
                        "/n/session/x", "committee_session", null, "lang-test"));
        assertThat(db.queryForObject("SELECT title FROM notification WHERE user_id = ? AND dedupe_key = 'lang-test'",
                String.class, f.member)).isEqualTo("Séance S-1 le 2026-11-02");
    }

    @Test
    void dashboardsAndReportsWorkAndStayInsideTheTenant() {
        MeetingFixture f = new MeetingFixture();
        Fixtures.Tenant other = Fixtures.tenant("DB" + System.nanoTime() % 100000);
        String otherCommittee = Owner.jdbc().queryForObject("SELECT code FROM committee WHERE id = ?", String.class, other.committeeId());

        f.as(f.member, () -> {
            Map<String, Object> m = dashboards.member();
            assertThat((List<?>) m.get("myFiles")).isNotEmpty();
            assertThat((List<?>) m.get("myAttendance")).hasSize(1);
            assertThatThrownBy(() -> dashboards.chair(f.committee)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> dashboards.cbm()).isInstanceOf(ApiException.class);
        });
        f.as(f.chair, () -> {
            Map<String, Object> c = dashboards.chair(f.committee);
            assertThat((List<?>) c.get("contractsNearCeiling")).hasSize(1);
            assertThat((List<?>) c.get("bidValidityExpiringSoon")).hasSize(1);
            assertThat((List<?>) c.get("turnaroundByType")).isNotEmpty();
            // Another tenant's committee is invisible, not forbidden-by-role.
            assertThatThrownBy(() -> dashboards.chair(other.committeeId())).isInstanceOf(ApiException.class);
            Map<String, Object> r = dashboards.summary(Time.today().minusDays(30), Time.today(), f.committee);
            assertThat(r.toString()).contains("held=1");
            assertThat(new String(dashboards.summaryCsv(null, null, f.committee).getBody())).contains("Attendance")
                    .contains("Claire Dissent");
        });
        f.as(f.cbm, () -> {
            Map<String, Object> c = dashboards.cbm();
            assertThat(c.toString()).contains(f.abbr + "-A").doesNotContain(otherCommittee);
            assertThat((List<?>) c.get("pendingRecommendations")).hasSize(2);
            @SuppressWarnings("unchecked")
            Map<String, Object> committee = ((List<Map<String, Object>>) c.get("committees")).get(0);
            assertThat((List<Object>) committee.get("complianceIssues")).contains("composition.no_vice_chairperson");
        });
    }
}
