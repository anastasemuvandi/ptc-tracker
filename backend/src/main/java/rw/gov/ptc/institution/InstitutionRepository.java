package rw.gov.ptc.institution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {
}
