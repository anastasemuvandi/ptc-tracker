package rw.gov.ptc.decision.service;

import rw.gov.ptc.decision.dto.DraftRequest;
import rw.gov.ptc.decision.dto.VoteRequest;
import rw.gov.ptc.decision.entity.Decision;
import rw.gov.ptc.decision.entity.Vote;

import java.util.Map;
import java.util.UUID;

/**
 * The decision log: a recommendation per agenda item, the vote on it with
 * dissent, and its issue to the CBM. The voting threshold is a share of the
 * members PRESENT and eligible (not of the committee size, which is the
 * quorum base). Each decision stores the configuration it was judged under.
 */
public interface DecisionService {

    Decision get(UUID id);

    Map<String, Object> forItem(UUID agendaItemId);

    Decision draft(UUID agendaItemId, DraftRequest req);

    Decision update(UUID id, DraftRequest req);

    Decision openVoting(UUID id);

    Vote vote(UUID id, VoteRequest req);

    Decision closeVoting(UUID id);

    Decision issue(UUID id);

    Decision submitToCbm(UUID id);
}
