package rw.gov.ptc.calendar.service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Working-day arithmetic in Africa/Kigali local dates against the national
 * holiday calendar, adjusted by the current institution's own ADD/REMOVE
 * rows when it has holiday_override set.
 */
public interface WorkingDayCalendar {

    Set<LocalDate> holidays(LocalDate from, LocalDate to);

    boolean isWorkingDay(LocalDate date);

    /** The date n working days after start (start itself not counted). */
    LocalDate addWorkingDays(LocalDate start, int n);

    /** Working days after from, up to and including to (0 if to <= from). */
    int workingDaysBetween(LocalDate from, LocalDate to);
}
