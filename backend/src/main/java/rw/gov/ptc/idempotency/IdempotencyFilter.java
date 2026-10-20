package rw.gov.ptc.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.tenancy.TenantContext;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every authenticated mutating API request carries a client-generated UUID
 * in the Idempotency-Key header. The first request with a key is executed
 * and its 2xx response stored; a retry with the same key (e.g. after a
 * dropped connection) receives the stored response instead of creating a
 * duplicate record. Non-2xx outcomes release the key so the client may
 * retry after fixing the cause (e.g. completing step-up).
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JdbcTemplate jdbc;

    public IdempotencyFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !MUTATING.contains(request.getMethod()) || !uri.startsWith("/api/")
                || uri.startsWith("/api/auth/") || TenantContext.userOrNull() == null
                || (request.getContentType() != null && request.getContentType().startsWith("multipart/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String keyHeader = request.getHeader(HEADER);
        if (!Ids.isCanonical(keyHeader)) {
            write(response, 400, "{\"code\":\"idempotency_key_required\",\"message\":\"Idempotency-Key header (UUID) is required\",\"params\":{}}");
            return;
        }
        UUID key = UUID.fromString(keyHeader);
        UUID user = TenantContext.userOrNull();
        byte[] body = request.getInputStream().readAllBytes();
        String hash = sha256(request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString() + "\n",
                body);

        int inserted = jdbc.update("""
                INSERT INTO idempotency_record (id, institution_id, user_id, idempotency_key, request_method,
                                                request_path, request_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """, Ids.newId(), TenantContext.institutionOrNull(), user, key, request.getMethod(),
                request.getRequestURI(), hash);

        if (inserted == 0) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT request_hash, response_status, response_body FROM idempotency_record WHERE user_id = ? AND idempotency_key = ?",
                    user, key);
            if (rows.isEmpty()) {
                write(response, 409, "{\"code\":\"request_in_progress\",\"message\":\"Retry shortly\",\"params\":{}}");
                return;
            }
            Map<String, Object> row = rows.get(0);
            if (!hash.equals(row.get("request_hash"))) {
                write(response, 422, "{\"code\":\"idempotency_key_reused\",\"message\":\"This key was used for a different request\",\"params\":{}}");
                return;
            }
            Object status = row.get("response_status");
            if (status == null) {
                write(response, 409, "{\"code\":\"request_in_progress\",\"message\":\"The original request is still being processed\",\"params\":{}}");
                return;
            }
            response.setHeader("Idempotent-Replay", "true");
            write(response, (Integer) status, (String) row.get("response_body"));
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), wrapped);
            int status = wrapped.getStatus();
            if (status >= 200 && status < 300) {
                jdbc.update("UPDATE idempotency_record SET response_status = ?, response_body = ? WHERE user_id = ? AND idempotency_key = ?",
                        status, new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8), user, key);
                completed = true;
            }
        } finally {
            if (!completed) {
                jdbc.update("DELETE FROM idempotency_record WHERE user_id = ? AND idempotency_key = ?", user, key);
            }
            wrapped.copyBodyToResponse();
        }
    }

    private static void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (body != null) {
            response.getWriter().write(body);
        }
    }

    private static String sha256(String prefix, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(prefix.getBytes(StandardCharsets.UTF_8));
            md.update(body);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Request whose body has already been read into memory for hashing. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
