package rw.gov.ptc.contract.service;

import rw.gov.ptc.contract.dto.ContractRequest;
import rw.gov.ptc.contract.entity.Contract;

import java.util.List;
import java.util.UUID;

/**
 * Contract references (the signed contract itself lives in Umucyo). Changes
 * to value or completion date happen only through type C/D files.
 */
public interface ContractService {

    List<Contract> list();

    Contract get(UUID id);

    Contract create(ContractRequest req);

    Contract update(UUID id, ContractRequest req);
}
