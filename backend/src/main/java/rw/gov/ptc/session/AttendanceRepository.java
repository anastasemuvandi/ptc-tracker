package rw.gov.ptc.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findBySessionId(UUID sessionId);

    Optional<Attendance> findBySessionIdAndMembershipId(UUID sessionId, UUID membershipId);
}
