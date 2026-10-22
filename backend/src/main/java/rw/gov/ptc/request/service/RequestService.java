package rw.gov.ptc.request.service;

import rw.gov.ptc.request.dto.AcceptRequest;
import rw.gov.ptc.request.dto.RequestInput;
import rw.gov.ptc.request.dto.ScheduleRequest;
import rw.gov.ptc.request.entity.PtcRequest;
import rw.gov.ptc.request.entity.RequestDocument;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requests from employees outside the committee. A requester drafts a
 * request, attaches internal supporting documents, declares they hold no
 * bid documents or bidder prices, and submits it; a committee officer
 * accepts it as a file or returns it with a reason. The requester follows
 * the file's progress without seeing the committee's deliberation.
 */
public interface RequestService {

    List<Map<String, Object>> mine();

    /** Submitted and decided requests, for committee officers (and those who read every committee). */
    List<Map<String, Object>> inbox(String status);

    Map<String, Object> detail(UUID id);

    PtcRequest create(RequestInput req);

    PtcRequest update(UUID id, RequestInput req);

    PtcRequest submit(UUID id, boolean declarationConfirmed);

    PtcRequest withdraw(UUID id);

    RequestDocument upload(UUID id, String fileName, String contentType, byte[] content);

    void deleteDocument(UUID id, UUID documentId);

    /** With content, for download by the requester or a reviewer. */
    RequestDocument document(UUID id, UUID documentId);

    PtcRequest accept(UUID id, AcceptRequest req);

    /**
     * Accepts a submitted request as a file of the session's committee and
     * puts it on that session's agenda, in one step.
     */
    PtcRequest schedule(UUID id, ScheduleRequest req);

    PtcRequest returnToRequester(UUID id, String reason);

    /** The request behind a file, if any, for the file's page. */
    Map<String, Object> forFile(UUID fileId);
}
