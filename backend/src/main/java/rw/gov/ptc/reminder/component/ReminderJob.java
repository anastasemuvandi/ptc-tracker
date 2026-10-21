package rw.gov.ptc.reminder.component;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import rw.gov.ptc.reminder.service.ReminderScanner;

/** Quartz entry point for the reminder engine. */
@DisallowConcurrentExecution
public class ReminderJob extends QuartzJobBean {

    @Autowired
    private ReminderScanner scanner;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        scanner.scanAll();
    }
}
