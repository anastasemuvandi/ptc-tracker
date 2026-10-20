package rw.gov.ptc.contractchange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmendmentRequestRepository extends JpaRepository<AmendmentRequest, UUID> {

    Optional<AmendmentRequest> findByFileId(UUID fileId);

    List<AmendmentRequest> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
