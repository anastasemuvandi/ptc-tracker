package rw.gov.ptc.institution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findAllByOrderByFullNameAsc();

    List<AppUser> findByIsProcurementOfficerTrueAndStatusOrderByFullNameAsc(String status);
}
