package rw.gov.ptc.session.dto;

import java.util.List;
import java.util.UUID;

public record AddItem(UUID fileId, String itemType, String adminKind, String adminNote, List<String> activities) {
}
