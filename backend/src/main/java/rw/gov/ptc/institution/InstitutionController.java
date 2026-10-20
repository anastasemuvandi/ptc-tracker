package rw.gov.ptc.institution;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class InstitutionController {

    public record CreateInstitution(String name, String abbreviation, String institutionType,
                                    String supervisingAuthority, String defaultCurrency,
                                    String adminEmail, String adminFullName) {
    }

    public record UpdateInstitution(String name, String institutionType, String supervisingAuthority,
                                    String minutesLetterhead, String minutesFontFamily, Integer minutesFontSize,
                                    Boolean holidayOverride, String defaultCurrency, Long version) {
    }

    private final InstitutionRepository institutions;
    private final IdentityAdmin identity;
    private final Access access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public InstitutionController(InstitutionRepository institutions, IdentityAdmin identity, Access access,
                                 AuditService audit, JdbcTemplate jdbc) {
        this.institutions = institutions;
        this.identity = identity;
        this.access = access;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // --------------------------------------------------- platform administrator

    /** Operational metrics only — never file content. */
    @GetMapping("/api/platform/metrics")
    public List<Map<String, Object>> metrics() {
        access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        return Rows.camel(jdbc.queryForList("SELECT * FROM platform_metrics()"));
    }

    @PostMapping("/api/platform/institutions")
    public Map<String, Object> create(@RequestBody CreateInstitution req) {
        access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        if (blank(req.name()) || blank(req.abbreviation()) || blank(req.institutionType())
                || blank(req.adminEmail()) || blank(req.adminFullName())) {
            throw ApiException.badRequest("institution.invalid", "Name, abbreviation, type and first administrator are required");
        }
        Institution inst = new Institution();
        inst.name = req.name().trim();
        inst.abbreviation = req.abbreviation().trim().toUpperCase();
        inst.institutionType = req.institutionType();
        inst.supervisingAuthority = req.supervisingAuthority();
        if (!blank(req.defaultCurrency())) {
            inst.defaultCurrency = req.defaultCurrency().toUpperCase();
        }
        saveInstitution(inst);
        UUID adminId = identity.invite(new IdentityAdmin.Invitation(inst.id, req.adminEmail(), req.adminFullName(),
                null, null, false, "en", Set.of(InstitutionRole.INSTITUTION_ADMIN)), access.userId());
        return Map.of("id", inst.id, "adminUserId", adminId);
    }

    @Transactional
    void saveInstitution(Institution inst) {
        institutions.save(inst);
        audit.record("CREATE", "institution", inst.id, null, inst);
    }

    @PutMapping("/api/platform/institutions/{id}/status")
    @Transactional
    public Institution setStatus(@PathVariable UUID id, @RequestParam String status) {
        access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        if (!Set.of("ACTIVE", "SUSPENDED").contains(status)) {
            throw ApiException.badRequest("institution.invalid_status", "Unknown status");
        }
        Institution inst = institutions.findById(id).orElseThrow(() -> ApiException.notFound("Institution"));
        var before = audit.snapshot(inst);
        inst.status = status;
        institutions.save(inst);
        audit.updated("institution", id, before, inst);
        return inst;
    }

    // ------------------------------------------------------- own institution

    @GetMapping("/api/institution")
    public Institution mine() {
        return institutions.findById(TenantContext.requireInstitution())
                .orElseThrow(() -> ApiException.notFound("Institution"));
    }

    @PutMapping("/api/institution")
    @Transactional
    public Institution update(@RequestBody UpdateInstitution req) {
        access.requireInstitutionAdmin();
        Institution inst = mine();
        if (req.version() != null && !req.version().equals(inst.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(inst);
        if (!blank(req.name())) inst.name = req.name().trim();
        if (!blank(req.institutionType())) inst.institutionType = req.institutionType();
        if (req.supervisingAuthority() != null) inst.supervisingAuthority = req.supervisingAuthority();
        if (req.minutesLetterhead() != null) inst.minutesLetterhead = req.minutesLetterhead();
        if (!blank(req.minutesFontFamily())) inst.minutesFontFamily = req.minutesFontFamily();
        if (req.minutesFontSize() != null) {
            if (req.minutesFontSize() < 8 || req.minutesFontSize() > 16) {
                throw ApiException.badRequest("institution.font_size", "Font size must be between 8 and 16");
            }
            inst.minutesFontSize = req.minutesFontSize();
        }
        if (req.holidayOverride() != null) inst.holidayOverride = req.holidayOverride();
        if (!blank(req.defaultCurrency())) inst.defaultCurrency = req.defaultCurrency().toUpperCase();
        institutions.save(inst);
        audit.updated("institution", inst.id, before, inst);
        return inst;
    }

    /** Upload the institution's .docx minutes template (letterhead, styles). */
    @PostMapping(value = "/api/institution/minutes-template", consumes = "multipart/form-data")
    @Transactional
    public Institution uploadTemplate(@RequestParam("file") MultipartFile file) throws IOException {
        access.requireInstitutionAdmin();
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw ApiException.badRequest("institution.template_size", "Template must be a .docx under 5 MB");
        }
        byte[] bytes = file.getBytes();
        // .docx is a ZIP container: "PK\003\004"
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw ApiException.badRequest("institution.template_format", "Template must be a .docx file");
        }
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            doc.getParagraphs();
        } catch (Exception e) {
            throw ApiException.badRequest("institution.template_format", "Template must be a valid .docx file");
        }
        Institution inst = mine();
        inst.minutesTemplateDocx = bytes;
        institutions.save(inst);
        audit.record("MINUTES_TEMPLATE_UPLOADED", "institution", inst.id, null,
                Map.of("filename", String.valueOf(file.getOriginalFilename()), "size", bytes.length));
        return inst;
    }

    @GetMapping("/api/institution/minutes-template")
    public ResponseEntity<byte[]> downloadTemplate() {
        Institution inst = mine();
        if (!inst.isHasMinutesTemplate()) {
            throw ApiException.notFound("Template");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"minutes-template.docx\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .body(inst.minutesTemplateDocx);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
