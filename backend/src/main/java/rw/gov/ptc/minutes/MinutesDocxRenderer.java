package rw.gov.ptc.minutes;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Renders minutes content to .docx with Apache POI. With an institution
 * template, the template's headers, footers, page setup and styles are kept
 * and its body is replaced; otherwise a plain document with the letterhead
 * text, font family and size from the institution settings is produced.
 */
@Component
public class MinutesDocxRenderer {

    public record Style(String fontFamily, int fontSize, String letterhead) {
    }

    private static final Map<String, String> ITEM_TYPES = Map.of(
            "A", "Evaluation report review", "B", "ToR / tender document review",
            "C", "Contract amendment (addendum)", "D", "Contract extension (time)",
            "E", "Prequalification review", "F", "Tender cancellation / rejection of all bids",
            "G", "Framework agreement annual evaluation", "H", "Referred matter", "I", "Administrative");

    private static final Map<String, String> STAGES = Map.of(
            "A1", "Preliminary / eligibility examination", "A2", "Technical evaluation",
            "A3", "Financial evaluation", "A4", "Administrative compliance (first-ranked bidder)",
            "A5", "Due diligence on the successful bidder", "A6", "Recommendation");

    public byte[] render(JsonNode content, byte[] template, Style style) throws IOException {
        XWPFDocument doc;
        if (template != null && template.length > 0) {
            doc = new XWPFDocument(new ByteArrayInputStream(template));
            for (int i = doc.getBodyElements().size() - 1; i >= 0; i--) {
                doc.removeBodyElement(i);
            }
        } else {
            doc = new XWPFDocument();
            JsonNode header = content.path("header");
            var h = doc.createHeader(HeaderFooterType.DEFAULT);
            String letterhead = style.letterhead() != null && !style.letterhead().isBlank()
                    ? style.letterhead() : header.path("institutionName").asText("");
            for (String line : letterhead.split("\\r?\\n")) {
                XWPFParagraph p = h.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                run(p, line, true, style.fontSize() + 1, style);
            }
            var f = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph fp = f.createParagraph();
            fp.setAlignment(ParagraphAlignment.CENTER);
            run(fp, content.path("disclaimer").asText(), false, Math.max(style.fontSize() - 2, 7), style);
        }
        new Writer(doc, style).write(content);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return out.toByteArray();
    }

    private static XWPFRun run(XWPFParagraph p, String text, boolean bold, int size, Style style) {
        XWPFRun r = p.createRun();
        r.setText(text == null ? "" : text);
        r.setBold(bold);
        r.setFontFamily(style.fontFamily());
        r.setFontSize(size);
        return r;
    }

    private static final class Writer {
        private final XWPFDocument doc;
        private final Style style;

        Writer(XWPFDocument doc, Style style) {
            this.doc = doc;
            this.style = style;
        }

        void write(JsonNode c) {
            JsonNode h = c.path("header");
            para(c.path("disclaimer").asText(), false, true);
            title("MINUTES OF THE " + h.path("committeeName").asText().toUpperCase() + " — SESSION "
                    + h.path("sessionNumber").asText());
            line("Institution", h.path("institutionName").asText());
            line("Committee", h.path("committeeName").asText() + " (" + h.path("committeeCode").asText() + ")");
            line("Session", h.path("sessionNumber").asText() + " — "
                    + ("EXTRAORDINARY".equals(h.path("kind").asText()) ? "Extraordinary" : "Ordinary") + " session");
            line("Date and time", h.path("date").asText() + " at " + h.path("time").asText() + " (Kigali time)");
            line("Venue", h.path("location").asText("—"));
            if (h.path("shortNotice").asBoolean()) {
                line("Convened at short notice", h.path("shortNoticeReason").asText());
            }

            heading("1. Attendance");
            XWPFTable t = table(List.of("Member", "Role", "Attendance"));
            for (JsonNode a : c.path("attendance")) {
                row(t, a.path("fullName").asText(), role(a.path("role").asText()), a.path("annotation").asText());
            }
            JsonNode q = c.path("quorum");
            if (q.has("required")) {
                para("Quorum: " + q.path("present").asInt() + " present of " + q.path("committeeSize").asInt()
                        + " members; " + q.path("required").asInt() + " required (" + q.path("rule").asText()
                        + "). Quorum " + (q.path("met").asBoolean() ? "was met." : "was NOT met."), false, false);
            } else {
                para("Present: " + q.path("present").asInt() + " of " + q.path("committeeSize").asInt() + " members.",
                        false, false);
            }

            heading("2. Agenda");
            for (JsonNode item : c.path("items")) {
                para(item.path("position").asInt() + ". " + itemLabel(item), false, false);
            }

            int n = 3;
            for (JsonNode item : c.path("items")) {
                heading(n++ + ". " + itemLabel(item));
                itemBody(item);
            }

            if (has(c, "secretaryNotes")) {
                heading(n++ + ". Other notes");
                for (String line : c.path("secretaryNotes").asText().split("\\r?\\n")) {
                    para(line, false, false);
                }
            }

            heading(n + ". Signatures");
            para("Members present sign these minutes. A member who did not vote for a decision signs with the "
                    + "dissent recorded above.", false, true);
            XWPFTable sig = table(List.of("Member", "Role", "Signature", "Date"));
            for (JsonNode s : c.path("signatures")) {
                row(sig, s.path("fullName").asText() + (s.path("dissented").asBoolean() ? " (dissent recorded)" : ""),
                        role(s.path("role").asText()), "", "");
            }
        }

        private String itemLabel(JsonNode item) {
            String type = item.path("itemType").asText();
            StringBuilder sb = new StringBuilder("[" + type + "] " + ITEM_TYPES.getOrDefault(type, type));
            if (!item.path("fileNumber").isNull() && !item.path("fileNumber").asText().isEmpty()) {
                sb.append(" — File ").append(item.path("fileNumber").asText());
            }
            if (!item.path("title").isNull() && !item.path("title").asText().isEmpty()) {
                sb.append(": ").append(item.path("title").asText());
            }
            if ("I".equals(type)) {
                sb.append(" — ").append(humanise(item.path("analysis").path("adminKind").asText()));
            }
            return sb.toString();
        }

        private void itemBody(JsonNode item) {
            if (has(item, "tenderRef")) line("Tender reference", item.path("tenderRef").asText());
            if (has(item, "contractRef")) line("Contract reference", item.path("contractRef").asText());
            if (has(item, "procurementType")) line("Procurement type", humanise(item.path("procurementType").asText()));
            if (has(item, "procurementMethod")) line("Method", item.path("procurementMethod").asText());

            JsonNode decl = item.path("declarations");
            if (decl.size() > 0) {
                sub("Declarations of interest");
                for (JsonNode d : decl) {
                    para(d.path("fullName").asText() + ": " + (d.path("hasInterest").asBoolean()
                            ? "declared a " + d.path("interestKind").asText("").toLowerCase() + " interest — "
                            + d.path("details").asText() + ". Excluded from the discussion and vote on this file."
                            : "no interest declared."), false, false);
                }
            }

            JsonNode a = item.path("analysis");
            switch (item.path("itemType").asText()) {
                case "A" -> evaluation(a);
                case "C" -> amendment(a);
                case "D" -> extension(a);
                case "B", "E", "F", "G" -> checklist(a);
                case "H" -> {
                    sub("Referral");
                    line("Referral reference", a.path("referralRef").asText());
                    line("Reason for referral", a.path("referralReason").asText());
                }
                case "I" -> {
                    if (has(a, "adminNote")) para(a.path("adminNote").asText(), false, false);
                }
                default -> {
                }
            }

            JsonNode d = item.path("decision");
            if (!d.isMissingNode()) {
                sub("Recommendation");
                para(humanise(d.path("recommendationCode").asText()) + ". " + d.path("recommendationText").asText(),
                        true, false);
                if (has(d, "recommendedBidderRef")) line("Recommended bidder (reference)", d.path("recommendedBidderRef").asText());
                if (has(d, "contractValue")) line("Contract value", d.path("contractValue").asText() + " " + d.path("currency").asText());
                if (d.path("structuredOutcome").isObject() && d.path("structuredOutcome").size() > 0) {
                    d.path("structuredOutcome").fields().forEachRemaining(e ->
                            line(humanise(e.getKey()), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
                }
                para("Votes: " + d.path("votesFor").asInt() + " for, " + d.path("votesAgainst").asInt() + " against, "
                        + d.path("votesAbstained").asInt() + " abstained. Required to adopt: "
                        + (d.path("requiredVotes").isNull() ? "—" : d.path("requiredVotes").asInt())
                        + " of " + (d.path("eligibleVoters").isNull() ? "—" : d.path("eligibleVoters").asInt())
                        + " eligible members present. Result: " + result(d.path("status").asText()) + ".", false, false);
                JsonNode dissents = item.path("dissents");
                if (dissents.size() > 0) {
                    sub("DISSENTING OPINIONS");
                    for (JsonNode ds : dissents) {
                        XWPFParagraph p = doc.createParagraph();
                        p.setIndentationLeft(360);
                        XWPFRun r = run(p, ds.path("fullName").asText() + " (" + choice(ds.path("choice").asText()) + "): ",
                                true, style.fontSize(), style);
                        r.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                        run(p, ds.path("reason").asText(), false, style.fontSize(), style);
                    }
                }
            }
        }

        private void evaluation(JsonNode a) {
            sub("Evaluation process");
            if (a.has("criteriaLocked")) {
                para(a.path("criteriaLocked").asBoolean()
                        ? "The committee confirmed it evaluated only against the criteria published in the tender document."
                        : "Criteria confirmation not recorded.", false, false);
            }
            if (has(a, "criteriaDefectFindings")) line("Findings on the published criteria", a.path("criteriaDefectFindings").asText());
            XWPFTable t = table(List.of("Stage", "Status", "Evaluated / passed", "Outcome"));
            for (JsonNode s : a.path("stages")) {
                row(t, s.path("code").asText() + " " + STAGES.getOrDefault(s.path("code").asText(), ""),
                        humanise(s.path("status").asText()),
                        s.path("evaluated").asInt() == 0 ? "—" : s.path("evaluated").asInt() + " / " + s.path("passed").asInt(),
                        s.path("outcome").asText(""));
            }
            for (JsonNode s : a.path("stages")) {
                for (JsonNode f : s.path("failures")) {
                    para(s.path("code").asText() + " — bidder " + f.path("bidderRef").asText() + " not retained: "
                            + humanise(f.path("failedRequirement").asText()) + ". " + f.path("reason").asText(), false, false);
                }
                if (s.path("rankedOrder").size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    Iterator<JsonNode> it = s.path("rankedOrder").elements();
                    int i = 1;
                    while (it.hasNext()) {
                        sb.append(i++).append(". ").append(it.next().asText()).append(it.hasNext() ? "; " : "");
                    }
                    line(s.path("code").asText() + " ranked order", sb.toString());
                }
            }
            for (JsonNode ap : a.path("abnormalPrices")) {
                para("Abnormal price (" + humanise(ap.path("direction").asText()) + ") — bidder " + ap.path("bidderRef").asText()
                        + ": written request " + ap.path("requestRef").asText("—") + " of " + ap.path("requestDate").asText("—")
                        + "; explanation received " + ap.path("explanationDate").asText("—") + ". Analysis: "
                        + ap.path("analysis").asText("—") + ". Decision: " + humanise(ap.path("decision").asText()) + " — "
                        + ap.path("decisionReasons").asText(""), false, false);
            }
            for (JsonNode cr : a.path("cureRequests")) {
                StringBuilder items = new StringBuilder();
                for (JsonNode i : cr.path("outstandingItems")) {
                    items.append(items.isEmpty() ? "" : ", ").append(humanise(i.path("code").asText(i.asText())));
                }
                para("Administrative compliance — bidder " + cr.path("bidderRef").asText() + ": requested " + items
                        + " on " + cr.path("issuedOn").asText() + ", deadline " + cr.path("deadline").asText() + " ("
                        + cr.path("workingDays").asInt() + " working days). Outcome: " + humanise(cr.path("outcome").asText())
                        + (has(cr, "outcomeNote") ? " — " + cr.path("outcomeNote").asText() : ""), false, false);
            }
            for (JsonNode dd : a.path("dueDiligence")) {
                para("Due diligence on bidder " + dd.path("bidderRef").asText() + " by " + dd.path("performedByName").asText()
                        + " on " + dd.path("performedOn").asText() + " (verified: " + dd.path("itemsVerified").asText()
                        + "). Result: " + humanise(dd.path("result").asText())
                        + (dd.path("disqualified").asBoolean() ? ". Disqualified: " + dd.path("disqualificationGround").asText() : "."),
                        false, false);
            }
            if (a.path("clarifications").size() > 0) {
                sub("Clarification log (a bidder may explain, not improve, its bid)");
                XWPFTable t2 = table(List.of("Bidder", "Issued", "Subject", "Response", "Treatment", "Effect"));
                for (JsonNode cl : a.path("clarifications")) {
                    row(t2, cl.path("bidderRef").asText(), cl.path("issuedOn").asText(), cl.path("subject").asText(),
                            cl.path("responseReceivedOn").asText("—"), cl.path("treatment").asText(""),
                            humanise(cl.path("effect").asText()));
                }
            }
        }

        private void amendment(JsonNode a) {
            sub("Cumulative value of amendments");
            if (has(a, "contractRef")) line("Contract", a.path("contractRef").asText());
            if (has(a, "description")) line("Requested change", a.path("description").asText());
            JsonNode c = a.path("calculation");
            String cur = c.path("currency").asText("");
            XWPFTable t = table(List.of("Item", "Value"));
            row(t, "Original contract value", c.path("originalValue").asText("—") + " " + cur);
            row(t, "Cumulative value of all previous amendments", c.path("priorApprovedCumulative").asText("—") + " " + cur);
            row(t, "Value now requested", c.path("requestedValue").asText("—") + " " + cur);
            row(t, "Resulting cumulative value", c.path("resultingCumulative").asText("—") + " " + cur);
            row(t, "Resulting cumulative percentage", c.path("resultingPct").asText("—") + " %");
            row(t, "Ceiling", c.path("ceilingPct").asText("—") + " %"
                    + (has(c, "absoluteCeiling") ? " and " + c.path("absoluteCeiling").asText() + " " + cur : ""));
            JsonNode ql = c.path("qualitative");
            para("Test 1 — qualitative (absolute bar): " + (ql.path("answered").asBoolean(ql.has("affectsSubstance"))
                    ? (ql.path("affectsSubstance").asBoolean() ? "the amendment AFFECTS the substance and nature of the contract — FAILS."
                    : "the amendment does not affect the substance and nature of the contract — passes.")
                    : "not answered.") + (has(ql, "justification") ? " Justification: " + ql.path("justification").asText() : ""),
                    false, false);
            JsonNode qt = c.path("quantitative");
            if (!qt.isMissingNode()) {
                para("Test 2 — quantitative: within percentage ceiling: " + yesNo(qt.path("withinPct")) + "; within absolute ceiling: "
                        + yesNo(qt.path("withinAbsolute")) + ". This test determines the approval route only; it does not cure "
                        + "failure of the qualitative test.", false, false);
            }
            if (has(c, "route")) {
                line("Approval route", "ESCALATED".equals(c.path("route").asText())
                        ? "Escalation to the supervising authority and inter-institutional panel" : "Entity-level approval");
            }
            JsonNode p = a.path("prompts");
            p.fields().forEachRemaining(e -> line(humanise(e.getKey()), e.getValue().asBoolean() ? "Yes" : "No"));
        }

        private void extension(JsonNode a) {
            sub("Time extension");
            line("Contract", a.path("contractRef").asText());
            line("Completion date before request", a.path("completionDateBefore").asText());
            line("Requested completion date", a.path("requestedCompletionDate").asText());
            line("Ground", humanise(a.path("groundCode").asText()) + " — " + a.path("groundDetails").asText());
            line("Request received", a.path("requestReceivedOn").asText() + " ("
                    + (a.path("receivedBeforeCompletion").asBoolean() ? "before" : "AFTER") + " the completion date)");
            if (has(a, "bidderFaultReasoning")) line("Reasoning (bidder's fault)", a.path("bidderFaultReasoning").asText());
            if (a.path("hasCostClaim").asBoolean()) {
                line("Cost claim", "Yes — also reviewed as an amendment" + (has(a, "linkedAmendmentFileNumber")
                        ? " (file " + a.path("linkedAmendmentFileNumber").asText() + ")" : ""));
            }
            if (has(a, "forceMajeureStart")) {
                line("Force majeure", a.path("forceMajeureStart").asText() + " to " + a.path("forceMajeureEnd").asText("ongoing"));
            }
        }

        private void checklist(JsonNode a) {
            if (a.path("checklist").size() == 0) {
                return;
            }
            sub("Checklist");
            XWPFTable t = table(List.of("Check", "Answer", "Note"));
            for (JsonNode c : a.path("checklist")) {
                row(t, humanise(c.path("itemCode").asText()), humanise(c.path("answer").asText()), c.path("note").asText(""));
            }
        }

        // ------------------------------------------------------------ helpers

        private void title(String text) {
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.setSpacingAfter(200);
            run(p, text, true, style.fontSize() + 3, style);
        }

        private void heading(String text) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingBefore(240);
            if (doc.getStyles() != null && doc.getStyles().styleExist("Heading1")) {
                p.setStyle("Heading1");
            }
            run(p, text, true, style.fontSize() + 2, style);
        }

        private void sub(String text) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingBefore(120);
            run(p, text, true, style.fontSize(), style);
        }

        private void para(String text, boolean bold, boolean italic) {
            XWPFParagraph p = doc.createParagraph();
            run(p, text, bold, style.fontSize(), style).setItalic(italic);
        }

        private void line(String label, String value) {
            XWPFParagraph p = doc.createParagraph();
            run(p, label + ": ", true, style.fontSize(), style);
            run(p, value == null ? "" : value, false, style.fontSize(), style);
        }

        private XWPFTable table(List<String> headers) {
            XWPFTable t = doc.createTable(1, headers.size());
            t.setWidth("100%");
            XWPFTableRow r = t.getRow(0);
            for (int i = 0; i < headers.size(); i++) {
                cell(r, i, headers.get(i), true);
            }
            return t;
        }

        private void row(XWPFTable t, String... values) {
            XWPFTableRow r = t.createRow();
            for (int i = 0; i < values.length; i++) {
                cell(r, i, values[i], false);
            }
        }

        private void cell(XWPFTableRow r, int i, String text, boolean bold) {
            var cell = r.getCell(i);
            XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
            run(p, text, bold, style.fontSize() - 1, style);
        }

        private static boolean has(JsonNode n, String field) {
            JsonNode v = n.path(field);
            return !v.isMissingNode() && !v.isNull() && !v.asText().isBlank();
        }

        private static String yesNo(JsonNode n) {
            return n.isMissingNode() || n.isNull() ? "—" : (n.asBoolean() ? "yes" : "NO");
        }

        private static String role(String r) {
            return humanise(r);
        }

        private static String choice(String c) {
            return switch (c) {
                case "AGAINST" -> "voted against";
                case "ABSTAIN" -> "abstained";
                default -> c.toLowerCase();
            };
        }

        private static String result(String status) {
            return switch (status) {
                case "ADOPTED", "ISSUED" -> "adopted";
                case "NOT_ADOPTED" -> "not adopted";
                default -> "pending";
            };
        }

        static String humanise(String code) {
            if (code == null || code.isBlank()) {
                return "";
            }
            String s = code.replace('_', ' ').toLowerCase();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
