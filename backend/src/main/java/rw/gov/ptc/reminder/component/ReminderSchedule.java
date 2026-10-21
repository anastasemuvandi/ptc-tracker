package rw.gov.ptc.reminder.component;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Scans every 15 minutes during the working day (Africa/Kigali). Dedupe
 * keys make repeated scans idempotent, so the cadence only affects how soon
 * a newly reached deadline is announced.
 */
@Configuration
@ConditionalOnProperty(name = "ptc.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderSchedule {

    @Bean
    public JobDetail reminderJobDetail() {
        return JobBuilder.newJob(ReminderJob.class).withIdentity("reminder-scan").storeDurably().build();
    }

    @Bean
    public Trigger reminderTrigger(JobDetail reminderJobDetail,
                                   @Value("${ptc.reminders.cron:0 0/15 7-19 * * ?}") String cron) {
        return TriggerBuilder.newTrigger().forJob(reminderJobDetail).withIdentity("reminder-scan-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(TimeZone.getTimeZone("Africa/Kigali")))
                .build();
    }
}
