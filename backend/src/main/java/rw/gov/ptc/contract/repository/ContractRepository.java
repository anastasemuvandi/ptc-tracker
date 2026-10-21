package rw.gov.ptc.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.contract.entity.Contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findAllByOrderByContractRefAsc();

    Optional<Contract> findByContractRef(String contractRef);
}
