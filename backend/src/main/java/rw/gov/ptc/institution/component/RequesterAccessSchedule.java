package rw.gov.ptc.institution.component;

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

/** Runs shortly after midnight (Africa/Kigali): access ends at the close of its last day. */
@Configuration
@ConditionalOnProperty(name = "ptc.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class RequesterAccessSchedule {

    @Bean
    public JobDetail requesterAccessJobDetail() {
        return JobBuilder.newJob(RequesterAccessJob.class).withIdentity("requester-access").storeDurably().build();
    }

    @Bean
    public Trigger requesterAccessTrigger(JobDetail requesterAccessJobDetail,
                                          @Value("${ptc.requester-access.cron:0 5 0 * * ?}") String cron) {
        return TriggerBuilder.newTrigger().forJob(requesterAccessJobDetail).withIdentity("requester-access-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(TimeZone.getTimeZone("Africa/Kigali")))
                .build();
    }
}
