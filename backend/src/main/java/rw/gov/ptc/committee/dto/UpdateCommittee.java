package rw.gov.ptc.committee.dto;

import java.time.LocalDate;

public record UpdateCommittee(String name, Integer size, String regulatorAuthRef, LocalDate regulatorAuthDate,
                              Long version) {
}
