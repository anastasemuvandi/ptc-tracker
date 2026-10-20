package rw.gov.ptc.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findAllByOrderByContractRefAsc();

    Optional<Contract> findByContractRef(String contractRef);
}
