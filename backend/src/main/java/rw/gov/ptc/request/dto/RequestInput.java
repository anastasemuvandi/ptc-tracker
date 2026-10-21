package rw.gov.ptc.request.dto;

public record RequestInput(String itemType, String title, String description, String contractRef, String tenderRef,
                           Long version) {
}
