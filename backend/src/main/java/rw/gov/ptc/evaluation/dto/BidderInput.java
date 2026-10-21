package rw.gov.ptc.evaluation.dto;

public record BidderInput(String bidderRef, String result, String failedRequirement, String failureReason,
                          Integer rank, Long version) {
}
