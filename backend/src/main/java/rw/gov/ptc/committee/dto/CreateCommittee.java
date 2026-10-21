package rw.gov.ptc.committee.dto;

import java.time.LocalDate;

public record CreateCommittee(String name, String code, Integer size, String regulatorAuthRef,
                              LocalDate regulatorAuthDate, LocalDate periodStart, String appointmentRef) {
}
