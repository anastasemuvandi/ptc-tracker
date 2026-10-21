package rw.gov.ptc.institution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.institution.entity.Institution;

import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {
}
