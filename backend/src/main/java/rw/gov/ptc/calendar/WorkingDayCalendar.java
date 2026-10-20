package rw.gov.ptc.calendar;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Working-day arithmetic in Africa/Kigali local dates against the national
 * holiday calendar, adjusted by the current institution's own ADD/REMOVE
 * rows when it has holiday_override set.
 */
@Service
public class WorkingDayCalendar {

    private final JdbcTemplate jdbc;

    public WorkingDayCalendar(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<LocalDate> holidays(LocalDate from, LocalDate to) {
        Set<LocalDate> result = new HashSet<>();
        jdbc.query("""
                SELECT h.holiday_date, h.institution_id IS NULL AS platform, h.kind,
                       coalesce((SELECT holiday_override FROM institution WHERE id = app_current_institution()), false) AS override
                  FROM holiday h
                 WHERE h.holiday_date BETWEEN ? AND ?
                 ORDER BY (h.institution_id IS NULL) DESC
                """, rs -> {
            LocalDate d = rs.getDate(1).toLocalDate();
            boolean platform = rs.getBoolean(2);
            boolean override = rs.getBoolean(4);
            if (platform) {
                result.add(d);
            } else if (override) {
                if ("REMOVE".equals(rs.getString(3))) {
                    result.remove(d);
                } else {
                    result.add(d);
                }
            }
        }, Date.valueOf(from), Date.valueOf(to));
        return result;
    }

    public boolean isWorkingDay(LocalDate date) {
        return isWorkingDay(date, holidays(date, date));
    }

    private static boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek d = date.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    /** The date n working days after start (start itself not counted). */
    public LocalDate addWorkingDays(LocalDate start, int n) {
        Set<LocalDate> holidays = holidays(start, start.plusDays(n * 3L + 30));
        LocalDate d = start;
        int counted = 0;
        while (counted < n) {
            d = d.plusDays(1);
            if (isWorkingDay(d, holidays)) {
                counted++;
            }
        }
        return d;
    }

    /** Working days after from, up to and including to (0 if to <= from). */
    public int workingDaysBetween(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        Set<LocalDate> holidays = holidays(from, to);
        int count = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d, holidays)) {
                count++;
            }
        }
        return count;
    }
}
