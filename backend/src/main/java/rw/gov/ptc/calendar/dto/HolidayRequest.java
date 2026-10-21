package rw.gov.ptc.calendar.dto;

import java.time.LocalDate;

public record HolidayRequest(LocalDate date, String name, String kind, String scope) {
}
