package rw.gov.ptc.contractchange.service;

import rw.gov.ptc.contract.entity.Contract;
import rw.gov.ptc.contractchange.dto.AmendmentInput;
import rw.gov.ptc.contractchange.dto.CostClaimInput;
import rw.gov.ptc.contractchange.dto.ExtensionInput;

import java.util.Map;
import java.util.UUID;

/**
 * Type C (amendment) and type D (time extension) files.
 *
 * <p>Status follows the decision log without depending on the decision
 * module: when the file's recommendation is ISSUED the assessment is frozen
 * (with the configuration it was judged under) and the request becomes
 * RECOMMENDED; when the CBM approves the recommendation the request becomes
 * APPROVED or REJECTED according to the recommendation. This reconciliation
 * runs on every read and is idempotent.
 */
public interface ContractChangeService {

    Map<String, Object> getAmendment(UUID fileId);

    Map<String, Object> createAmendment(UUID fileId, AmendmentInput in);

    Map<String, Object> updateAmendment(UUID fileId, AmendmentInput in);

    /** Freeze the assessment with the configuration it was judged under. */
    Map<String, Object> freezeAmendment(UUID fileId);

    Map<String, Object> getExtension(UUID fileId);

    Map<String, Object> createExtension(UUID fileId, ExtensionInput in);

    Map<String, Object> updateExtension(UUID fileId, ExtensionInput in);

    /**
     * A time extension that carries a cost claim is also an amendment: it
     * becomes a linked type C file and must satisfy both tests.
     */
    Map<String, Object> raiseCostClaim(UUID fileId, CostClaimInput in);

    /** Brings every amendment and extension on the contract in line with the decision log. */
    void reconcileContract(Contract c);
}
