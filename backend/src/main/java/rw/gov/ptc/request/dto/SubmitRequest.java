package rw.gov.ptc.request.dto;

/** The requester confirms the documents hold no bid documents, bidder submissions or prices. */
public record SubmitRequest(boolean declarationConfirmed) {
}
