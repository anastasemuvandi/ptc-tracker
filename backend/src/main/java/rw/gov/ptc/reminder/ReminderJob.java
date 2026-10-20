package rw.gov.ptc.reminder;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

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
