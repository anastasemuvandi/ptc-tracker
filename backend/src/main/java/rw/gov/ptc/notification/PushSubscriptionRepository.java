package rw.gov.ptc.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
}
