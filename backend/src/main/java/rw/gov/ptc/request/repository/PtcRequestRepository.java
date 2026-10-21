package rw.gov.ptc.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.request.entity.PtcRequest;

import java.util.Optional;
import java.util.UUID;

public interface PtcRequestRepository extends JpaRepository<PtcRequest, UUID> {

    Optional<PtcRequest> findByFileId(UUID fileId);
}
