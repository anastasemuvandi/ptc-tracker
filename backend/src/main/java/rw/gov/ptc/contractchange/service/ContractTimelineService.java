package rw.gov.ptc.contractchange.service;

import java.util.Map;
import java.util.UUID;

/**
 * Every amendment and extension against one contract over its life, with
 * the running cumulative amendment value (approved amendments, in the
 * contract currency), the current completion date and the performance
 * security status.
 */
public interface ContractTimelineService {

    Map<String, Object> timeline(UUID contractId);
}
