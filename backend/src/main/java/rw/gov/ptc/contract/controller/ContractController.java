package rw.gov.ptc.contract.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.contract.dto.ContractRequest;
import rw.gov.ptc.contract.entity.Contract;
import rw.gov.ptc.contract.service.ContractService;

import java.util.List;
import java.util.UUID;

/** Contract CRUD. The contract timeline lives in contractchange (types C/D). */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contracts;

    public ContractController(ContractService contracts) {
        this.contracts = contracts;
    }

    @GetMapping
    public List<Contract> list() {
        return contracts.list();
    }

    @PostMapping
    public Contract create(@RequestBody ContractRequest req) {
        return contracts.create(req);
    }

    @GetMapping("/{id}")
    public Contract get(@PathVariable UUID id) {
        return contracts.get(id);
    }

    @PutMapping("/{id}")
    public Contract update(@PathVariable UUID id, @RequestBody ContractRequest req) {
        return contracts.update(id, req);
    }
}
