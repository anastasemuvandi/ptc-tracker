package rw.gov.ptc.contractchange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, UUID> {

    Optional<ExtensionRequest> findByFileId(UUID fileId);

    List<ExtensionRequest> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
