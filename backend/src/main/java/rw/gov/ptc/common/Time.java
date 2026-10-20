package rw.gov.ptc.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Storage is UTC; local dates (deadlines, working days) are Africa/Kigali. */
public final class Time {

    public static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");

    private Time() {
    }

    public static LocalDate today() {
        return LocalDate.now(KIGALI);
    }

    public static LocalDate localDate(Instant instant) {
        return instant.atZone(KIGALI).toLocalDate();
    }

    /** Start of the given Kigali calendar day as a UTC instant. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KIGALI).toInstant();
    }
}
