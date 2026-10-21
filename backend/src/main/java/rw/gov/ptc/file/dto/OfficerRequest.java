package rw.gov.ptc.file.dto;

import java.util.UUID;

public record OfficerRequest(UUID officerId, String financialYear) {
}
