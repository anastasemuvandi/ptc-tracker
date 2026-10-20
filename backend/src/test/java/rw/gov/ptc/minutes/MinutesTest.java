package rw.gov.ptc.minutes;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinutesTest extends IntegrationTest {

    static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    MinutesService service;

    private static String text(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    @Test
    void docxRendersTypeAAndCWithDissentAndSignaturesAndNoUuids() throws Exception {
        MeetingFixture f = new MeetingFixture();
        AtomicReference<byte[]> docx = new AtomicReference<>();
        f.as(f.secretary, () -> {
            Minutes m = service.generate(f.session);
            docx.set(service.docx(m.id).bytes());
            assertThat(service.docx(m.id).filename()).contains("Minutes-").contains("2026-007").endsWith("-DRAFT.docx");
        });
        String text = text(docx.get());
        assertThat(text)
                .contains(f.sessionNumber)
                .contains(f.fileNumberA).contains(f.fileNumberC)
                .contains("TND-2026-0042").contains("CT-" + f.abbr)
                .contains("Internal working document — not an official procurement record")
                // Type A: stage outcomes, failure ground, ranked order, abnormal price exchange, clarification log
                .contains("A1 Preliminary").contains("Bid security issued for 60 days")
                .contains("1. BIDDER-002; 2. BIDDER-001")
                .contains("REF/2026/77").contains("Clarification log")
                // Type C: cumulative calculation and route
                .contains("120000000.00").contains("22.00").contains("Escalation to the supervising authority")
                // Conflict of interest and dissent
                .contains("Brother-in-law works for BIDDER-001")
                .contains("DISSENTING OPINIONS").contains("Claire Dissent (voted against)").contains(f.dissentReason)
                // Attendance annotations and signature block
                .contains("Absent with permission; apology received before the session")
                .contains("Signatures").contains("Alice Chair").contains("Claire Dissent (dissent recorded)")
                .contains("Required to adopt: 2 of 3");
        assertThat(UUID_PATTERN.matcher(text).find()).as("no UUIDs in minutes").isFalse();
    }

    @Test
    void institutionTemplateIsUsedAsBase() throws Exception {
        MeetingFixture f = new MeetingFixture();
        byte[] template;
        try (XWPFDocument t = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            t.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("OFFICIAL LETTERHEAD OF THE TEST AGENCY");
            t.createParagraph().createRun().setText("TEMPLATE BODY PLACEHOLDER");
            t.write(out);
            template = out.toByteArray();
        }
        Owner.jdbc().update("UPDATE institution SET minutes_template_docx = ? WHERE id = ?", template, f.institution);
        AtomicReference<byte[]> docx = new AtomicReference<>();
        f.as(f.secretary, () -> docx.set(service.docx(service.generate(f.session).id).bytes()));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx.get()))) {
            String headers = doc.getHeaderList().stream().map(h -> h.getText()).reduce("", String::concat);
            assertThat(headers).contains("OFFICIAL LETTERHEAD OF THE TEST AGENCY");
        }
        assertThat(text(docx.get())).doesNotContain("TEMPLATE BODY PLACEHOLDER").contains(f.fileNumberA);
    }

    @Test
    void finaliseRequiresEveryPresentMemberAndRegenerationInvalidatesAcknowledgements() {
        MeetingFixture f = new MeetingFixture();
        AtomicReference<UUID> id = new AtomicReference<>();
        f.as(f.secretary, () -> id.set(service.generate(f.session).id));
        f.as(f.secretary, () -> service.submitForReview(id.get()));
        f.as(f.chair, () -> service.acknowledge(id.get(), null));

        // Regenerating returns to draft with a new version: the chair must acknowledge again.
        f.as(f.secretary, () -> {
            service.generate(f.session);
            service.submitForReview(id.get());
            assertThat((List<?>) service.view(f.session).get("pendingAcknowledgements")).hasSize(4);
        });
        f.as(f.absent, () -> assertThatThrownBy(() -> service.acknowledge(id.get(), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("present"));
        f.as(f.chair, () -> service.acknowledge(id.get(), "Agreed"));
        f.as(f.secretary, () -> assertThatThrownBy(() -> service.finalise(id.get()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("minutes.acknowledgements_pending")));
        for (UUID u : List.of(f.secretary, f.dissenter, f.member)) {
            f.as(u, () -> service.acknowledge(id.get(), null));
        }
        f.as(f.secretary, () -> {
            assertThat(service.finalise(id.get()).status).isEqualTo("FINAL");
            assertThatThrownBy(() -> service.generate(f.session)).isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("minutes.final"));
            assertThat(service.docx(id.get()).filename()).doesNotContain("DRAFT");
        });
        Integer audits = Owner.jdbc().queryForObject(
                "SELECT count(*) FROM audit_event WHERE entity_id = ? AND action LIKE 'MINUTES_%'", Integer.class, id.get());
        assertThat(audits).isGreaterThanOrEqualTo(8);
    }

    @Test
    void ordinaryMemberCannotGenerate() {
        MeetingFixture f = new MeetingFixture();
        f.as(f.member, () -> assertThatThrownBy(() -> service.generate(f.session)).isInstanceOf(ApiException.class));
    }
}
