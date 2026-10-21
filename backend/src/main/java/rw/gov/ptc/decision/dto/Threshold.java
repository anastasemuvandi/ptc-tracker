package rw.gov.ptc.decision.dto;

public record Threshold(int present, int excluded, int eligible, int required, String rule) {
}
