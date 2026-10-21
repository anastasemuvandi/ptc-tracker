package rw.gov.ptc.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.request.entity.RequestDocument;

import java.util.UUID;

public interface RequestDocumentRepository extends JpaRepository<RequestDocument, UUID> {

    long countByRequestId(UUID requestId);
}
