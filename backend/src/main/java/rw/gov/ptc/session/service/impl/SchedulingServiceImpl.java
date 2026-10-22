package rw.gov.ptc.session.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.request.dto.ScheduleRequest;
import rw.gov.ptc.request.service.RequestService;
import rw.gov.ptc.session.dto.AddItem;
import rw.gov.ptc.session.dto.PlannedItem;
import rw.gov.ptc.session.dto.PlannedRequest;
import rw.gov.ptc.session.dto.SessionRequest;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.service.AgendaService;
import rw.gov.ptc.session.service.SchedulingService;
import rw.gov.ptc.session.service.SessionService;

import java.util.List;

@Service
public class SchedulingServiceImpl implements SchedulingService {

    private final SessionService sessions;
    private final AgendaService agenda;
    private final RequestService requests;
    private final ProcFileRepository files;

    public SchedulingServiceImpl(SessionService sessions, AgendaService agenda, RequestService requests,
                                 ProcFileRepository files) {
        this.sessions = sessions;
        this.agenda = agenda;
        this.requests = requests;
        this.files = files;
    }

    @Override
    @Transactional
    public CommitteeSession schedule(SessionRequest req) {
        CommitteeSession s = sessions.create(req);
        for (PlannedItem item : req.items() == null ? List.<PlannedItem>of() : req.items()) {
            ProcFile f = files.findById(item.fileId()).orElseThrow(() -> ApiException.notFound("File"));
            agenda.add(s.id, new AddItem(f.id, f.itemType, null, null, item.activities()));
        }
        for (PlannedRequest r : req.requests() == null ? List.<PlannedRequest>of() : req.requests()) {
            requests.schedule(r.requestId(), new ScheduleRequest(s.id, r.contractId(), null, r.activities()));
        }
        return s;
    }
}
