package rw.gov.ptc.file.service;

import rw.gov.ptc.file.dto.FileRequest;
import rw.gov.ptc.file.dto.Filter;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.entity.TableRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The files register: what is assigned to which committee and how far it has got. */
public interface FileService {

    /** Reads are limited to committees the user sits on, unless CBM / admin / read-only. */
    ProcFile get(UUID id);

    List<Map<String, Object>> list(Filter f);

    ProcFile create(FileRequest req);

    Map<String, Object> detail(UUID id);

    ProcFile update(UUID id, FileRequest req);

    ProcFile changeStage(UUID id, String toStage, String note);

    ProcFile reassign(UUID id, UUID toCommitteeId, String reason);

    ProcFile assignOfficer(UUID id, UUID officerId, String financialYear);

    TableRequest requestTabling(UUID fileId, String reason);

    List<Map<String, Object>> tableRequests(UUID committeeId, String status);

    TableRequest decideTabling(UUID requestId, boolean accept, String note, UUID sessionId);
}
