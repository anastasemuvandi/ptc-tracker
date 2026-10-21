package rw.gov.ptc.institution.component;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import rw.gov.ptc.institution.service.RequesterAccessService;

/** Quartz entry point: ends short-term requester access once its date has passed. */
@DisallowConcurrentExecution
public class RequesterAccessJob extends QuartzJobBean {

    @Autowired
    private RequesterAccessService service;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        service.offboardExpiredEverywhere();
    }
}
