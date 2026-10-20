package rw.gov.ptc.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.params()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        if (e.getRequiredType() == UUID.class) {
            return bad("invalid_uuid", "Malformed identifier: " + e.getName());
        }
        return bad("invalid_parameter", "Invalid value for " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException ife && ife.getTargetType() == UUID.class) {
            return bad("invalid_uuid", "Malformed identifier in request body");
        }
        return bad("invalid_body", "Request body could not be read");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", "Some fields are invalid", Map.of("fields", fields)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException e) {
        return bad("missing_header", "Missing header " + e.getHeaderName());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> optimistic(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("concurrent_modification",
                "This record was changed by someone else. Reload and try again.", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("forbidden", "You are not allowed to do this", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("not_found", "Not found", Map.of()));
    }

    /**
     * Database constraints are the last line of defence for procedural rules
     * (e.g. abnormal-price rejection without the written exchange). Surface
     * the constraint name so the client can explain which rule was broken.
     */
    @ExceptionHandler(PermissionDeniedDataAccessException.class)
    public ResponseEntity<ApiError> permission(PermissionDeniedDataAccessException e) {
        log.warn("Database permission denied: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("tenant_violation", "Not permitted for this institution", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        ServerErrorMessage server = serverMessage(e);
        if (server != null) {
            String constraint = server.getConstraint();
            if ("42501".equals(server.getSQLState())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiError("tenant_violation", "Not permitted for this institution", Map.of()));
            }
            if (constraint != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                        "constraint." + constraint, "The record breaks rule " + constraint,
                        Map.of("constraint", constraint)));
            }
        }
        log.warn("Integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("integrity_violation", "The change conflicts with existing data", Map.of()));
    }

    private static ServerErrorMessage serverMessage(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException p) {
                return p.getServerErrorMessage();
            }
        }
        return null;
    }

    private static ResponseEntity<ApiError> bad(String code, String message) {
        return ResponseEntity.badRequest().body(new ApiError(code, message, Map.of()));
    }
}
