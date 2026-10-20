package rw.gov.ptc.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Business error with a stable code. The code doubles as an i18n key on the
 * client (errors.&lt;code&gt;); the message is an English fallback.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> params) {
        super(message);
        this.status = status;
        this.code = code;
        this.params = params == null ? Map.of() : params;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, null);
    }

    public static ApiException badRequest(String code, String message, Map<String, Object> params) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, params);
    }

    /** A rule from configuration blocks the action (422 so the UI can explain it). */
    public static ApiException rule(String code, String message, Map<String, Object> params) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, params);
    }

    public static ApiException rule(String code, String message) {
        return rule(code, message, null);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found", Map.of("entity", what));
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, null);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, null);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, null);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }
}
