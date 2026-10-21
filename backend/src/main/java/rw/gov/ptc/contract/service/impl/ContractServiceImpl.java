package rw.gov.ptc.contract.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Enums;
import rw.gov.ptc.contract.dto.ContractRequest;
import rw.gov.ptc.contract.entity.Contract;
import rw.gov.ptc.contract.enums.ContractStatus;
import rw.gov.ptc.contract.repository.ContractRepository;
import rw.gov.ptc.contract.service.ContractService;
import rw.gov.ptc.security.Access;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Set<String> TYPES = Set.of("WORKS", "GOODS", "CONSULTANCY", "NON_CONSULTANCY");

    private final ContractRepository contracts;
    private final AuditService audit;
    private final Access access;

    public ContractServiceImpl(ContractRepository contracts, AuditService audit, Access access) {
        this.contracts = contracts;
        this.audit = audit;
        this.access = access;
    }

    @Override
    public List<Contract> list() {
        return contracts.findAllByOrderByContractRefAsc();
    }

    @Override
    public Contract get(UUID id) {
        return contracts.findById(id).orElseThrow(() -> ApiException.notFound("Contract"));
    }

    /** CBM, institution administrators and committee officers maintain contract references. */
    private void requireEditor() {
        var p = access.principal();
        if (p.has(InstitutionRole.CBM) || p.has(InstitutionRole.INSTITUTION_ADMIN)
                || p.committees().stream().anyMatch(g -> g.role().isOfficer())) {
            return;
        }
        throw ApiException.forbidden("forbidden", "You are not allowed to maintain contracts");
    }

    @Override
    @Transactional
    public Contract create(ContractRequest req) {
        requireEditor();
        if (req.contractRef() == null || req.contractRef().isBlank() || req.title() == null || req.title().isBlank()
                || req.currency() == null || req.originalValue() == null || req.originalCompletionDate() == null
                || req.procurementType() == null) {
            throw ApiException.badRequest("contract.invalid",
                    "Reference, title, procurement type, currency, original value and completion date are required");
        }
        if (contracts.findByContractRef(req.contractRef().trim()).isPresent()) {
            throw ApiException.conflict("contract.duplicate_ref", "A contract with this reference already exists");
        }
        Contract c = new Contract();
        c.contractRef = req.contractRef().trim();
        c.currency = req.currency().trim().toUpperCase();
        c.originalValue = req.originalValue();
        c.originalCompletionDate = req.originalCompletionDate();
        c.currentCompletionDate = req.originalCompletionDate();
        apply(c, req);
        c.createdBy = access.userId();
        contracts.saveAndFlush(c);
        audit.created("contract", c.id, c);
        return c;
    }

    @Override
    @Transactional
    public Contract update(UUID id, ContractRequest req) {
        requireEditor();
        Contract c = get(id);
        if (req.version() != null && !req.version().equals(c.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(c);
        apply(c, req);
        if (req.status() != null) {
            ContractStatus status = Enums.parse(ContractStatus.class, req.status());
            if (status == null) {
                throw ApiException.badRequest("contract.invalid_status", "Unknown status");
            }
            c.status = status;
        }
        contracts.saveAndFlush(c);
        audit.updated("contract", c.id, before, c);
        return c;
    }

    private void apply(Contract c, ContractRequest req) {
        if (req.title() != null && !req.title().isBlank()) c.title = req.title().trim();
        if (req.tenderRef() != null) c.tenderRef = req.tenderRef();
        if (req.supplierRef() != null) c.supplierRef = req.supplierRef();
        if (req.procurementType() != null) {
            if (!TYPES.contains(req.procurementType())) {
                throw ApiException.badRequest("contract.invalid_type", "Unknown procurement type");
            }
            c.procurementType = req.procurementType();
        }
        if (req.signedDate() != null) c.signedDate = req.signedDate();
        if (req.perfSecurityRef() != null) c.perfSecurityRef = req.perfSecurityRef();
        if (req.perfSecurityAmount() != null) {
            c.perfSecurityAmount = req.perfSecurityAmount();
            c.perfSecurityCurrency = req.perfSecurityCurrency() == null ? c.currency : req.perfSecurityCurrency().toUpperCase();
        }
        if (req.perfSecurityExpiry() != null) c.perfSecurityExpiry = req.perfSecurityExpiry();
        if (req.isFramework() != null) c.isFramework = req.isFramework();
        if (req.frameworkExpiry() != null) c.frameworkExpiry = req.frameworkExpiry();
        if (c.originalValue != null && c.originalValue.signum() <= 0) {
            throw ApiException.badRequest("contract.invalid_value", "Original value must be positive");
        }
    }
}
