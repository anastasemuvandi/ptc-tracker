package rw.gov.ptc.notification.dto;

import java.util.Map;

public record SubscriptionRequest(String endpoint, Map<String, String> keys) {
}
