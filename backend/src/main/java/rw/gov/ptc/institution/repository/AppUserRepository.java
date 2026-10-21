package rw.gov.ptc.institution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.institution.entity.AppUser;
import rw.gov.ptc.institution.enums.UserStatus;

import java.util.List;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findAllByOrderByFullNameAsc();

    List<AppUser> findByIsProcurementOfficerTrueAndStatusOrderByFullNameAsc(UserStatus status);
}
