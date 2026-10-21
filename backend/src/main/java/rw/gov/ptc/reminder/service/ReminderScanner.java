package rw.gov.ptc.reminder.service;

import java.time.LocalDate;

/**
 * The deadline and reminder engine. For every active institution, inside
 * that tenant's scope, scans the configured deadlines and notifies the
 * people who must act. Every notification carries a dedupe key naming the
 * occurrence (entity + date), so repeated scans never send twice, while a
 * changed date (e.g. an extended validity) produces a fresh alert.
 */
public interface ReminderScanner {

    void scanAll();

    /** Runs within a tenant scope. */
    void scanInstitution(LocalDate today);
}
