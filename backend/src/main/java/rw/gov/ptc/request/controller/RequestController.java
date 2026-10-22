package rw.gov.ptc.request.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import rw.gov.ptc.request.dto.AcceptRequest;
import rw.gov.ptc.request.dto.RequestInput;
import rw.gov.ptc.request.dto.ReturnRequest;
import rw.gov.ptc.request.dto.ScheduleRequest;
import rw.gov.ptc.request.dto.SubmitRequest;
import rw.gov.ptc.request.entity.PtcRequest;
import rw.gov.ptc.request.entity.RequestDocument;
import rw.gov.ptc.request.service.RequestService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @GetMapping("/requests/mine")
    public List<Map<String, Object>> mine() {
        return service.mine();
    }

    @GetMapping("/requests/inbox")
    public List<Map<String, Object>> inbox(@RequestParam(required = false) String status) {
        return service.inbox(status);
    }

    @PostMapping("/requests")
    public PtcRequest create(@RequestBody RequestInput req) {
        return service.create(req);
    }

    @GetMapping("/requests/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/requests/{id}")
    public PtcRequest update(@PathVariable UUID id, @RequestBody RequestInput req) {
        return service.update(id, req);
    }

    @PostMapping("/requests/{id}/submit")
    public PtcRequest submit(@PathVariable UUID id, @RequestBody SubmitRequest req) {
        return service.submit(id, req.declarationConfirmed());
    }

    @PostMapping("/requests/{id}/withdraw")
    public PtcRequest withdraw(@PathVariable UUID id) {
        return service.withdraw(id);
    }

    @PostMapping(value = "/requests/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RequestDocument upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return service.upload(id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    @DeleteMapping("/requests/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        service.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/requests/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID documentId) {
        RequestDocument d = service.document(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(d.content);
    }

    @PostMapping("/requests/{id}/accept")
    public PtcRequest accept(@PathVariable UUID id, @RequestBody AcceptRequest req) {
        return service.accept(id, req);
    }

    @PostMapping("/requests/{id}/schedule")
    public PtcRequest schedule(@PathVariable UUID id, @RequestBody ScheduleRequest req) {
        return service.schedule(id, req);
    }

    @PostMapping("/requests/{id}/return")
    public PtcRequest returnToRequester(@PathVariable UUID id, @RequestBody ReturnRequest req) {
        return service.returnToRequester(id, req.reason());
    }

    @GetMapping("/files/{fileId}/request")
    public ResponseEntity<Map<String, Object>> forFile(@PathVariable UUID fileId) {
        Map<String, Object> r = service.forFile(fileId);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }
}
