package rw.gov.ptc.common;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> params) {
}
