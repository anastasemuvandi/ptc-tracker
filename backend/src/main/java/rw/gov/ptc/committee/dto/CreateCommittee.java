package rw.gov.ptc.committee.dto;

import java.time.LocalDate;

/** The CBM names the committee and schedules the start of its first period; the end follows from the period length. */
public record CreateCommittee(String name, LocalDate periodStart) {
}
