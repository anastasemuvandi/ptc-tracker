package rw.gov.ptc.minutes.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.minutes.dto.AckRequest;
import rw.gov.ptc.minutes.dto.Download;
import rw.gov.ptc.minutes.dto.NotesRequest;
import rw.gov.ptc.minutes.entity.Minutes;
import rw.gov.ptc.minutes.service.MinutesService;

import java.util.Map;
import java.util.UUID;

@RestController
public class MinutesController {

    private final MinutesService service;

    public MinutesController(MinutesService service) {
        this.service = service;
    }

    @PostMapping("/api/sessions/{sessionId}/minutes/generate")
    public Minutes generate(@PathVariable UUID sessionId) {
        return service.generate(sessionId);
    }

    @GetMapping("/api/sessions/{sessionId}/minutes")
    public Map<String, Object> view(@PathVariable UUID sessionId) {
        return service.view(sessionId);
    }

    @PutMapping("/api/minutes/{id}")
    public Minutes update(@PathVariable UUID id, @RequestBody NotesRequest req) {
        return service.updateNotes(id, req.secretaryNotes(), req.version());
    }

    @PostMapping("/api/minutes/{id}/submit-for-review")
    public Minutes submit(@PathVariable UUID id) {
        return service.submitForReview(id);
    }

    @PostMapping("/api/minutes/{id}/acknowledge")
    public Map<String, Object> acknowledge(@PathVariable UUID id, @RequestBody(required = false) AckRequest req) {
        return service.acknowledge(id, req == null ? null : req.comment());
    }

    @PostMapping("/api/minutes/{id}/finalise")
    public Minutes finalise(@PathVariable UUID id) {
        return service.finalise(id);
    }

    /** Generated server-side; the browser receives a download. */
    @GetMapping("/api/minutes/{id}/docx")
    public ResponseEntity<byte[]> docx(@PathVariable UUID id) {
        Download d = service.docx(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(d.bytes());
    }
}
